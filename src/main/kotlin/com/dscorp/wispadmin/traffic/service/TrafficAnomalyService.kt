package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.traffic.config.TrafficProperties
import com.dscorp.wispadmin.traffic.entity.*
import com.dscorp.wispadmin.traffic.repository.*
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import kotlin.math.abs

@Service
open class TrafficAnomalyService(
    private val hourlyRepository: SubscriptionTrafficHourlyRepository,
    private val dailyRepository: SubscriptionTrafficDailyRepository,
    private val sourceRunRepository: TrafficSourceRunRepository,
    private val anomalyRepository: TrafficAnomalyEventRepository,
    private val properties: TrafficProperties
) {
    @Scheduled(fixedDelayString = "\${traffic.poll.interval-ms:300000}", initialDelayString = "\${traffic.poll.initial-delay-ms:120000}")
    fun scheduledEvaluate() { if (properties.anomaly.enabled) evaluate() }

    @Transactional
    open fun evaluate(now: LocalDateTime = LocalDateTime.now()) {
        evaluateSources(now)
        evaluateHourly(now)
        evaluateDaily(now)
    }

    private fun batchSize() = properties.anomaly.evaluationBatchSize.coerceAtLeast(1)

    private fun evaluateHourly(now: LocalDateTime) {
        val from = now.minusDays(HOURLY_BASELINE_DAYS)
        hourlyRepository.findDistinctSubscriptionIdsInBucketRange(from, now)
            .chunked(batchSize())
            .forEach { batch ->
                hourlyRepository.findInBucketRangeForSubscriptions(from, now, batch)
                    .groupBy { it.subscriptionId }
                    .forEach { (subscriptionId, history) ->
                        evaluateSubscription(subscriptionId, history.sortedBy { it.bucketStart }, now)
                    }
            }
    }

    private fun evaluateSources(now: LocalDateTime) {
        sourceRunRepository.findTop100ByOrderByStartedAtDesc().distinctBy { it.hostDeviceId }.forEach { run ->
            val coverage = if (run.expectedCount == 0) 0.0 else run.writtenCount * 100.0 / run.expectedCount
            setState(TrafficAnomalyType.TRAFFIC_MISSING, "router:${run.hostDeviceId}", null, run.hostDeviceId, coverage < properties.anomaly.minimumCoveragePct, now, 100.0, coverage, coverage - 100.0, coverage, coverage / 100.0, "{\"fact\":\"collector coverage below threshold\"}")
        }
    }

    private fun evaluateSubscription(id: Int, rows: List<SubscriptionTrafficHourly>, now: LocalDateTime) {
        val current = rows.lastOrNull() ?: return
        val recent = rows.filter { it.bucketStart >= now.minusHours(1) }
        val coverage = recent.map { it.coveragePct }.averageOrZero()
        val zeroTraffic = recent.isNotEmpty() && recent.all { it.rxBytesTotal + it.txBytesTotal == 0L } && coverage >= 90
        val historicalActive = rows.filter { it.bucketStart < now.minusHours(1) }.takeLast(28).map { it.rxBytesTotal + it.txBytesTotal }.medianLong() > 0
        setState(TrafficAnomalyType.NO_TRAFFIC, "subscription:$id", id, null, zeroTraffic && historicalActive, now, null, 0.0, null, coverage, coverage / 100.0, "{\"fact\":\"valid observations contain no transferred bytes\"}")

        val utilization = current.utilizationDownPct ?: 0.0
        val saturated = coverage >= properties.anomaly.minimumCoveragePct && (utilization >= properties.anomaly.saturationPct || current.secondsOver80 >= 900)
        setState(TrafficAnomalyType.PLAN_SATURATION, "subscription:$id", id, null, saturated, now, properties.anomaly.saturationPct, utilization, utilization - properties.anomaly.saturationPct, coverage, coverage / 100.0, "{\"fact\":\"traffic demand near plan limit\"}")

        val baselineRows = rows.filter { it.bucketStart < current.bucketStart && it.bucketStart.hour == current.bucketStart.hour }.takeLast(28)
        val baselineValues = baselineRows.map { it.avgMbpsDown }
        if (baselineValues.size >= 7) {
            val baseline = baselineValues.median(); val mad = baselineValues.map { abs(it - baseline) }.median().coerceAtLeast(.01); val deviation = current.avgMbpsDown - baseline
            val spike = deviation > 3 * mad && current.avgMbpsDown > baseline * 2
            val drop = deviation < -3 * mad && current.avgMbpsDown < baseline * .3
            val confidence = (coverage / 100.0 * (baselineValues.size / 28.0)).coerceIn(0.0, 1.0)
            setState(TrafficAnomalyType.TRAFFIC_SPIKE, "subscription:$id", id, null, spike, now, baseline, current.avgMbpsDown, deviation, coverage, confidence, "{\"method\":\"median_mad_same_hour\"}")
            setState(TrafficAnomalyType.TRAFFIC_DROP, "subscription:$id", id, null, drop, now, baseline, current.avgMbpsDown, deviation, coverage, confidence, "{\"method\":\"median_mad_same_hour\"}")
        }
    }

    private fun evaluateDaily(now: LocalDateTime) {
        val from = now.toLocalDate().minusDays(DAILY_BASELINE_DAYS)
        val to = now.toLocalDate().plusDays(1)
        dailyRepository.findDistinctSubscriptionIdsInBucketRange(from, to)
            .chunked(batchSize())
            .forEach { batch ->
                dailyRepository.findInBucketRangeForSubscriptions(from, to, batch)
                    .groupBy { it.subscriptionId }
                    .forEach { (id, history) -> evaluateDailySubscription(id, history, now) }
            }
    }

    private fun evaluateDailySubscription(id: Int, history: List<SubscriptionTrafficDaily>, now: LocalDateTime) {
        val current = history.maxByOrNull { it.bucketStart } ?: return
        val baselineValues = history.filter { it.bucketStart < current.bucketStart && it.bucketStart.dayOfWeek == current.bucketStart.dayOfWeek }.map { (it.rxBytesTotal + it.txBytesTotal).toDouble() }
        if (baselineValues.size < 4) return
        val baseline = baselineValues.median(); val mad = baselineValues.map { abs(it - baseline) }.median().coerceAtLeast(1.0); val observed = (current.rxBytesTotal + current.txBytesTotal).toDouble(); val deviation = observed - baseline
        setState(TrafficAnomalyType.PATTERN_DEVIATION, "subscription:$id", id, null, abs(deviation) > 3 * mad, now, baseline, observed, deviation, current.coveragePct, current.coveragePct / 100.0, "{\"method\":\"median_mad_same_weekday\"}")
    }

    private fun setState(type: TrafficAnomalyType, scope: String, subscriptionId: Int?, routerId: Int?, active: Boolean, now: LocalDateTime, baseline: Double?, observed: Double?, deviation: Double?, coverage: Double, confidence: Double, evidence: String) {
        val existing = when {
            subscriptionId != null -> anomalyRepository.findTopByAnomalyTypeAndSubscriptionIdAndEventStatusOrderByStartedAtDesc(type, subscriptionId, TrafficAnomalyStatus.OPEN)
            routerId != null -> anomalyRepository.findTopByAnomalyTypeAndHostDeviceIdAndEventStatusOrderByStartedAtDesc(type, routerId, TrafficAnomalyStatus.OPEN)
            else -> null
        }
        if (active) {
            val key = "${properties.anomaly.ruleVersion}:$type:$scope:${now.toLocalDate()}:${now.hour}:${now.minute}"
            val event = existing ?: TrafficAnomalyEvent(dedupeKey = key, anomalyType = type, subscriptionId = subscriptionId, hostDeviceId = routerId, startedAt = now)
            event.eventStatus = TrafficAnomalyStatus.OPEN; event.endedAt = null; event.lastEvaluatedAt = now; event.baselineValue = baseline; event.observedValue = observed; event.deviationValue = deviation; event.coveragePct = coverage.coerceIn(0.0, 100.0); event.confidence = confidence.coerceIn(0.0, 1.0); event.ruleVersion = properties.anomaly.ruleVersion; event.evidenceJson = evidence
            anomalyRepository.save(event)
        } else if (existing?.eventStatus == TrafficAnomalyStatus.OPEN) {
            existing.eventStatus = TrafficAnomalyStatus.CLOSED; existing.endedAt = now; existing.lastEvaluatedAt = now; anomalyRepository.save(existing)
        }
    }

    private fun List<Double>.median(): Double { if (isEmpty()) return 0.0; val s = sorted(); val m = s.size / 2; return if (s.size % 2 == 0) (s[m - 1] + s[m]) / 2 else s[m] }
    private fun List<Long>.medianLong(): Long { if (isEmpty()) return 0; val s = sorted(); return s[s.size / 2] }
    private fun List<Double>.averageOrZero() = if (isEmpty()) 0.0 else average()

    companion object {
        private const val HOURLY_BASELINE_DAYS = 35L
        private const val DAILY_BASELINE_DAYS = 36L
    }
}
