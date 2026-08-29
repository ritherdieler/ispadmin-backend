package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.traffic.config.TrafficProperties
import com.dscorp.wispadmin.traffic.entity.TrafficAggregationLayer
import com.dscorp.wispadmin.traffic.entity.TrafficAggregationRun
import com.dscorp.wispadmin.traffic.entity.TrafficAggregationRunStatus
import com.dscorp.wispadmin.traffic.entity.TrafficAggregationWatermark
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficFiveMinuteRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficHourlyRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficSampleRepository
import com.dscorp.wispadmin.traffic.repository.TrafficAggregationRunRepository
import com.dscorp.wispadmin.traffic.repository.TrafficAggregationWatermarkRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicBoolean

@Service
open class TrafficAggregationJobService(
    private val watermarkRepository: TrafficAggregationWatermarkRepository,
    private val runRepository: TrafficAggregationRunRepository,
    private val sampleRepository: SubscriptionTrafficSampleRepository,
    private val fiveMinuteRepository: SubscriptionTrafficFiveMinuteRepository,
    private val hourlyRepository: SubscriptionTrafficHourlyRepository,
    private val rollupService: SubscriptionTrafficRollupService,
    private val trafficProperties: TrafficProperties
) {
    companion object {
        private val logger = LoggerFactory.getLogger(TrafficAggregationJobService::class.java)
        private val fiveMinuteRunning = AtomicBoolean(false)
        private val hourlyRunning = AtomicBoolean(false)
        private val dailyRunning = AtomicBoolean(false)
    }

    data class AggregationLayerHealth(
        val layer: TrafficAggregationLayer,
        val consolidatedThrough: LocalDateTime?,
        val lagSeconds: Long
    )

    open fun catchUpFiveMinute(now: LocalDateTime = LocalDateTime.now()): TrafficAggregationRun {
        if (!fiveMinuteRunning.compareAndSet(false, true)) {
            return skippedRun(TrafficAggregationLayer.FIVE_MINUTE, "five-minute catch-up already running")
        }
        val started = LocalDateTime.now()
        val run = runRepository.save(
            TrafficAggregationRun(layer = TrafficAggregationLayer.FIVE_MINUTE, startedAt = started, status = TrafficAggregationRunStatus.RUNNING)
        )
        return try {
            ensureOneMinuteCutover(now)
            val closedThrough = lastClosedFiveMinute(now)
            val watermark = readWatermark(TrafficAggregationLayer.FIVE_MINUTE)?.consolidatedThrough
            val earliest = sampleRepository.findMinBucketStart()
            if (earliest == null || closedThrough == null) {
                return finish(run, TrafficAggregationRunStatus.SKIPPED, null, null, 0, 0, 0, null, started)
            }
            var cursor = (watermark ?: earliest.minusMinutes(5)).plusMinutes(5)
            if (cursor.isAfter(closedThrough)) {
                return finish(run, TrafficAggregationRunStatus.SKIPPED, cursor, closedThrough, 0, 0, 0, null, started)
            }
            val cutover = resolveOneMinuteSince()
            val chunkHours = trafficProperties.aggregation.catchUpChunkHours.coerceAtLeast(1)
            var rowsRead = 0
            var windows = 0
            while (!cursor.isAfter(closedThrough)) {
                val chunkEndExclusive = minOf(cursor.plusHours(chunkHours.toLong()), closedThrough.plusMinutes(5))
                rollupService.rollupFiveMinute(cursor, chunkEndExclusive, cutover)
                val lastInChunk = chunkEndExclusive.minusMinutes(5)
                if (!lastInChunk.isBefore(cursor)) {
                    writeWatermark(TrafficAggregationLayer.FIVE_MINUTE, minOf(lastInChunk, closedThrough))
                }
                rowsRead += 1
                windows++
                cursor = chunkEndExclusive
            }
            writeWatermark(TrafficAggregationLayer.FIVE_MINUTE, closedThrough)
            finish(run, TrafficAggregationRunStatus.OK, watermark ?: earliest, closedThrough.plusMinutes(5), rowsRead, windows, 0, null, started)
        } catch (ex: Exception) {
            logger.warn("Five-minute catch-up failed: {}", ex.message)
            finish(run, TrafficAggregationRunStatus.FAILED, null, null, 0, 0, 0, ex.message, started)
        } finally {
            fiveMinuteRunning.set(false)
        }
    }

    open fun catchUpHourly(now: LocalDateTime = LocalDateTime.now()): TrafficAggregationRun {
        if (!hourlyRunning.compareAndSet(false, true)) {
            return skippedRun(TrafficAggregationLayer.HOURLY, "hourly catch-up already running")
        }
        val started = LocalDateTime.now()
        val run = runRepository.save(
            TrafficAggregationRun(layer = TrafficAggregationLayer.HOURLY, startedAt = started, status = TrafficAggregationRunStatus.RUNNING)
        )
        return try {
            val closedThrough = lastClosedHour(now) ?: return finish(run, TrafficAggregationRunStatus.SKIPPED, null, null, 0, 0, 0, null, started)
            val fiveMinuteThrough = readWatermark(TrafficAggregationLayer.FIVE_MINUTE)?.consolidatedThrough
                ?: return finish(run, TrafficAggregationRunStatus.SKIPPED, null, null, 0, 0, 0, "five-minute watermark missing", started)
            val target = minOf(closedThrough, truncateHour(fiveMinuteThrough))
            val watermark = readWatermark(TrafficAggregationLayer.HOURLY)?.consolidatedThrough
            val earliest = fiveMinuteRepository.findMinBucketStart()?.let { truncateHour(it) }
                ?: return finish(run, TrafficAggregationRunStatus.SKIPPED, null, null, 0, 0, 0, null, started)
            var cursor = (watermark ?: earliest.minusHours(1)).plusHours(1)
            if (cursor.isAfter(target)) {
                return finish(run, TrafficAggregationRunStatus.SKIPPED, cursor, target, 0, 0, 0, null, started)
            }
            val chunkHours = trafficProperties.aggregation.catchUpChunkHours.coerceAtLeast(1)
            var rowsRead = 0
            var windows = 0
            while (!cursor.isAfter(target)) {
                val chunkEnd = minOf(cursor.plusHours(chunkHours.toLong()), target.plusHours(1))
                val rows = fiveMinuteRepository.findInBucketRange(cursor, chunkEnd)
                rowsRead += rows.size
                rollupService.rollupHourly(cursor, chunkEnd)
                windows++
                cursor = chunkEnd
            }
            writeWatermark(TrafficAggregationLayer.HOURLY, target)
            finish(run, TrafficAggregationRunStatus.OK, watermark ?: earliest, target.plusHours(1), rowsRead, windows, 0, null, started)
        } catch (ex: Exception) {
            logger.warn("Hourly catch-up failed: {}", ex.message)
            finish(run, TrafficAggregationRunStatus.FAILED, null, null, 0, 0, 0, ex.message, started)
        } finally {
            hourlyRunning.set(false)
        }
    }

    open fun catchUpDaily(now: LocalDateTime = LocalDateTime.now()): TrafficAggregationRun {
        if (!dailyRunning.compareAndSet(false, true)) {
            return skippedRun(TrafficAggregationLayer.DAILY, "daily catch-up already running")
        }
        val started = LocalDateTime.now()
        val run = runRepository.save(
            TrafficAggregationRun(layer = TrafficAggregationLayer.DAILY, startedAt = started, status = TrafficAggregationRunStatus.RUNNING)
        )
        return try {
            val closedDay = now.toLocalDate().minusDays(1)
            val hourlyThrough = readWatermark(TrafficAggregationLayer.HOURLY)?.consolidatedThrough?.toLocalDate()
                ?: return finish(run, TrafficAggregationRunStatus.SKIPPED, null, null, 0, 0, 0, "hourly watermark missing", started)
            val targetDay = minOf(closedDay, hourlyThrough)
            val watermarkDay = readWatermark(TrafficAggregationLayer.DAILY)?.consolidatedThrough?.toLocalDate()
            val earliest = hourlyRepository.findMinBucketStart()?.toLocalDate()
                ?: return finish(run, TrafficAggregationRunStatus.SKIPPED, null, null, 0, 0, 0, null, started)
            var cursor = (watermarkDay ?: earliest.minusDays(1)).plusDays(1)
            if (cursor.isAfter(targetDay)) {
                return finish(run, TrafficAggregationRunStatus.SKIPPED, cursor.atStartOfDay(), targetDay.atStartOfDay(), 0, 0, 0, null, started)
            }
            var windows = 0
            while (!cursor.isAfter(targetDay)) {
                rollupService.rollupDaily(cursor)
                windows++
                cursor = cursor.plusDays(1)
            }
            writeWatermark(TrafficAggregationLayer.DAILY, targetDay.atStartOfDay())
            rollupService.rollupMonthly(YearMonth.from(now))
            finish(run, TrafficAggregationRunStatus.OK, watermarkDay?.atStartOfDay() ?: earliest.atStartOfDay(), targetDay.plusDays(1).atStartOfDay(), windows, windows, 0, null, started)
        } catch (ex: Exception) {
            logger.warn("Daily catch-up failed: {}", ex.message)
            finish(run, TrafficAggregationRunStatus.FAILED, null, null, 0, 0, 0, ex.message, started)
        } finally {
            dailyRunning.set(false)
        }
    }

    open fun layerHealth(now: LocalDateTime = LocalDateTime.now()): List<AggregationLayerHealth> {
        val five = readWatermark(TrafficAggregationLayer.FIVE_MINUTE)?.consolidatedThrough
        val hourly = readWatermark(TrafficAggregationLayer.HOURLY)?.consolidatedThrough
        val daily = readWatermark(TrafficAggregationLayer.DAILY)?.consolidatedThrough
        return listOf(
            AggregationLayerHealth(TrafficAggregationLayer.FIVE_MINUTE, five, lagSeconds(five, lastClosedFiveMinute(now))),
            AggregationLayerHealth(TrafficAggregationLayer.HOURLY, hourly, lagSeconds(hourly, lastClosedHour(now))),
            AggregationLayerHealth(TrafficAggregationLayer.DAILY, daily, lagSeconds(daily, now.toLocalDate().minusDays(1).atStartOfDay()))
        )
    }

    open fun resolveOneMinuteSince(): LocalDateTime? {
        readWatermark(TrafficAggregationLayer.ONE_MINUTE_SINCE)?.consolidatedThrough?.let { return it }
        val configured = trafficProperties.aggregation.oneMinuteSince?.trim().orEmpty()
        if (configured.isNotEmpty()) {
            return try {
                LocalDateTime.parse(configured)
            } catch (_: DateTimeParseException) {
                null
            }
        }
        return null
    }

    private fun ensureOneMinuteCutover(now: LocalDateTime) {
        if (trafficProperties.poll.bucketMinutes != 1) return
        if (readWatermark(TrafficAggregationLayer.ONE_MINUTE_SINCE)?.consolidatedThrough != null) return
        val configured = resolveOneMinuteSince()
        writeWatermark(TrafficAggregationLayer.ONE_MINUTE_SINCE, configured ?: now.truncatedTo(ChronoUnit.MINUTES))
    }

    private fun lastClosedFiveMinute(now: LocalDateTime): LocalDateTime? {
        val truncated = now.withMinute((now.minute / 5) * 5).withSecond(0).withNano(0)
        return truncated.minusMinutes(5)
    }

    private fun lastClosedHour(now: LocalDateTime): LocalDateTime? =
        now.truncatedTo(ChronoUnit.HOURS).minusHours(1)

    private fun truncateHour(value: LocalDateTime): LocalDateTime = value.truncatedTo(ChronoUnit.HOURS)

    private fun lagSeconds(consolidated: LocalDateTime?, target: LocalDateTime?): Long {
        if (target == null) return 0
        if (consolidated == null) return Duration.between(LocalDateTime.of(2000, 1, 1, 0, 0), target).seconds.coerceAtLeast(0)
        return Duration.between(consolidated, target).seconds.coerceAtLeast(0)
    }

    private fun readWatermark(layer: TrafficAggregationLayer): TrafficAggregationWatermark? =
        watermarkRepository.findById(layer).orElse(null)

    private fun writeWatermark(layer: TrafficAggregationLayer, through: LocalDateTime) {
        val existing = readWatermark(layer) ?: TrafficAggregationWatermark(layer = layer)
        existing.consolidatedThrough = through
        existing.updatedAt = LocalDateTime.now()
        watermarkRepository.save(existing)
    }

    private fun skippedRun(layer: TrafficAggregationLayer, reason: String): TrafficAggregationRun {
        val started = LocalDateTime.now()
        return runRepository.save(
            TrafficAggregationRun(
                layer = layer,
                startedAt = started,
                completedAt = started,
                status = TrafficAggregationRunStatus.SKIPPED,
                errorMessage = reason.take(500),
                durationMs = 0
            )
        )
    }

    private fun finish(
        run: TrafficAggregationRun,
        status: TrafficAggregationRunStatus,
        from: LocalDateTime?,
        to: LocalDateTime?,
        rowsRead: Int,
        rowsWritten: Int,
        pending: Int,
        error: String?,
        started: LocalDateTime
    ): TrafficAggregationRun {
        val completed = LocalDateTime.now()
        run.status = status
        run.completedAt = completed
        run.windowFrom = from
        run.windowTo = to
        run.rowsRead = rowsRead
        run.rowsWritten = rowsWritten
        run.pendingWindows = pending
        run.errorMessage = error?.take(500)
        run.durationMs = ChronoUnit.MILLIS.between(started, completed)
        return runRepository.save(run)
    }
}
