package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.traffic.dto.*
import com.dscorp.wispadmin.traffic.entity.*
import com.dscorp.wispadmin.traffic.port.TrafficDirectoryPort
import com.dscorp.wispadmin.traffic.port.TrafficDirectoryTarget
import com.dscorp.wispadmin.traffic.repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.ceil

interface TrafficEvidenceProvider {
    fun observations(subscriptionId: Int, from: LocalDateTime, to: LocalDateTime): BandwidthSeriesDto
    fun anomalies(subscriptionId: Int, from: LocalDateTime, to: LocalDateTime): List<BandwidthAnomalyDto>
}

@Service
open class BandwidthIntelligenceService(
    private val directory: TrafficDirectoryPort,
    private val routerRepository: TrafficRouterRepository,
    private val sampleRepository: SubscriptionTrafficSampleRepository,
    private val fiveMinuteRepository: SubscriptionTrafficFiveMinuteRepository,
    private val hourlyRepository: SubscriptionTrafficHourlyRepository,
    private val dailyRepository: SubscriptionTrafficDailyRepository,
    private val sourceRunRepository: TrafficSourceRunRepository,
    private val anomalyRepository: TrafficAnomalyEventRepository,
    private val aggregationJobService: TrafficAggregationJobService
) : TrafficEvidenceProvider {

    private data class MetricRow(val subscriptionId: Int, val bucket: LocalDateTime, val rx: Long, val tx: Long, val avgDown: Double, val avgUp: Double, val p95Down: Double, val p95Up: Double, val coverage: Double)

    @Transactional(readOnly = true)
    open fun network(from: LocalDateTime, to: LocalDateTime, resolution: String, routerId: Int?, planId: Int?): BandwidthNetworkDto {
        val effective = effectiveResolution(from, to, resolution, network = true)
        val series = networkSeries(from, to, effective, routerId, planId)
        return BandwidthNetworkDto(series = series, overview = overviewFromSeries(from, to, effective, series, routerId, planId))
    }

    @Transactional(readOnly = true)
    open fun overview(from: LocalDateTime, to: LocalDateTime, resolution: String, routerId: Int?, planId: Int?): BandwidthOverviewDto =
        network(from, to, resolution, routerId, planId).overview

    private fun overviewFromSeries(
        from: LocalDateTime,
        to: LocalDateTime,
        effective: String,
        series: BandwidthSeriesDto,
        routerId: Int?,
        planId: Int?
    ): BandwidthOverviewDto {
        val points = series.points
        val subscriptions = eligibleClients(routerId, planId)
        val planCapacity = subscriptions.sumOf { it.planDownloadMbps ?: 0 }.takeIf { it > 0 }
        val p95Down = percentile(points.map { it.avgMbpsDown }, .95)
        return BandwidthOverviewDto(
            meta = series.meta,
            totalRxBytes = points.sumOf { it.rxBytes },
            totalTxBytes = points.sumOf { it.txBytes },
            avgMbpsDown = points.map { it.avgMbpsDown }.averageOrZero(),
            avgMbpsUp = points.map { it.avgMbpsUp }.averageOrZero(),
            p95MbpsDown = p95Down,
            p95MbpsUp = percentile(points.map { it.avgMbpsUp }, .95),
            peakMbpsDown = points.maxOfOrNull { it.avgMbpsDown } ?: 0.0,
            peakMbpsUp = points.maxOfOrNull { it.avgMbpsUp } ?: 0.0,
            utilizationPct = planCapacity?.let { p95Down * 100.0 / it },
            growthPct = growthFromPoints(points),
            activeSubscriptions = countActiveSubscriptions(from, to, effective, routerId, planId, subscriptions).toInt(),
            openAnomalies = countOpenAnomalies(routerId, planId, subscriptions)
        )
    }

    private fun countOpenAnomalies(routerId: Int?, planId: Int?, subscriptions: List<EligibleClient>): Int {
        if (routerId == null && planId == null) {
            return anomalyRepository.countByEventStatus(TrafficAnomalyStatus.OPEN).toInt()
        }
        val ids = subscriptions.map { it.subscriptionId }.toSet()
        return if (ids.isEmpty()) 0 else anomalyRepository.countOpenForSubscriptions(ids).toInt()
    }

    @Transactional(readOnly = true)
    open fun series(from: LocalDateTime, to: LocalDateTime, resolution: String, routerId: Int?, planId: Int?, subscriptionId: Int? = null): BandwidthSeriesDto {
        if (subscriptionId != null) {
            val effective = effectiveResolution(from, to, resolution, network = false)
            val rows = metricRows(from, to, effective, setOf(subscriptionId))
            return seriesFromRows(from, to, effective, rows)
        }
        val effective = effectiveResolution(from, to, resolution, network = true)
        return networkSeries(from, to, effective, routerId, planId)
    }

    private fun networkSeries(from: LocalDateTime, to: LocalDateTime, effective: String, routerId: Int?, planId: Int?): BandwidthSeriesDto {
        val points = when (effective) {
            "5m" -> aggregateFiveMinute(from, to, routerId, planId)
            "1d" -> aggregateDaily(from, to, routerId, planId)
            else -> aggregateHourly(from, to, routerId, planId)
        }
        return BandwidthSeriesDto(meta(from, to, effective, points.map { it.coveragePct }), points)
    }

    private fun aggregateFiveMinute(from: LocalDateTime, to: LocalDateTime, routerId: Int?, planId: Int?): List<BandwidthPointDto> {
        val rows = when {
            planId != null -> {
                val ids = eligibleSubscriptionIds(routerId, planId)
                if (ids.isEmpty()) emptyList() else fiveMinuteRepository.aggregateNetworkBucketsBySubscriptions(from, to, ids)
            }
            routerId != null -> fiveMinuteRepository.aggregateNetworkBucketsByHost(from, to, routerId)
            else -> fiveMinuteRepository.aggregateNetworkBuckets(from, to)
        }
        return rows.map { toPoint(it.getBucketStart(), it) }
    }

    private fun aggregateHourly(from: LocalDateTime, to: LocalDateTime, routerId: Int?, planId: Int?): List<BandwidthPointDto> {
        val rows = if (routerId == null && planId == null) {
            hourlyRepository.aggregateNetworkBuckets(from, to)
        } else {
            val ids = eligibleSubscriptionIds(routerId, planId)
            if (ids.isEmpty()) emptyList() else hourlyRepository.aggregateNetworkBucketsBySubscriptions(from, to, ids)
        }
        return rows.map { toPoint(it.getBucketStart(), it) }
    }

    private fun aggregateDaily(from: LocalDateTime, to: LocalDateTime, routerId: Int?, planId: Int?): List<BandwidthPointDto> {
        val dayFrom = from.toLocalDate()
        val dayTo = to.toLocalDate().plusDays(1)
        val rows = if (routerId == null && planId == null) {
            dailyRepository.aggregateNetworkBuckets(dayFrom, dayTo)
        } else {
            val ids = eligibleSubscriptionIds(routerId, planId)
            if (ids.isEmpty()) emptyList() else dailyRepository.aggregateNetworkBucketsBySubscriptions(dayFrom, dayTo, ids)
        }
        return rows.map { toPoint(it.getBucketStart().atStartOfDay(), it) }
    }

    private fun toPoint(bucket: LocalDateTime, row: BandwidthNetworkBucketProjection) = BandwidthPointDto(
        bucket.toString(),
        row.getRxBytes(),
        row.getTxBytes(),
        row.getAvgMbpsDown(),
        row.getAvgMbpsUp(),
        row.getP95MbpsDown(),
        row.getP95MbpsUp(),
        row.getCoveragePct()
    )

    private fun toPoint(bucket: LocalDateTime, row: BandwidthNetworkDayBucketProjection) = BandwidthPointDto(
        bucket.toString(),
        row.getRxBytes(),
        row.getTxBytes(),
        row.getAvgMbpsDown(),
        row.getAvgMbpsUp(),
        row.getP95MbpsDown(),
        row.getP95MbpsUp(),
        row.getCoveragePct()
    )

    private fun countActiveSubscriptions(
        from: LocalDateTime,
        to: LocalDateTime,
        effective: String,
        routerId: Int?,
        planId: Int?,
        subscriptions: List<EligibleClient>
    ): Long = when (effective) {
        "5m" -> when {
            planId != null -> {
                val ids = subscriptions.map { it.subscriptionId }.toSet()
                if (ids.isEmpty()) 0L else fiveMinuteRepository.countDistinctSubscriptionsByIds(from, to, ids)
            }
            routerId != null -> fiveMinuteRepository.countDistinctSubscriptionsByHost(from, to, routerId)
            else -> fiveMinuteRepository.countDistinctSubscriptions(from, to)
        }
        "1d" -> {
            val dayFrom = from.toLocalDate()
            val dayTo = to.toLocalDate().plusDays(1)
            if (routerId == null && planId == null) dailyRepository.countDistinctSubscriptions(dayFrom, dayTo)
            else {
                val ids = subscriptions.map { it.subscriptionId }.toSet()
                if (ids.isEmpty()) 0L else dailyRepository.countDistinctSubscriptionsByIds(dayFrom, dayTo, ids)
            }
        }
        else -> if (routerId == null && planId == null) hourlyRepository.countDistinctSubscriptions(from, to)
        else {
            val ids = subscriptions.map { it.subscriptionId }.toSet()
            if (ids.isEmpty()) 0L else hourlyRepository.countDistinctSubscriptionsByIds(from, to, ids)
        }
    }

    private fun seriesFromRows(from: LocalDateTime, to: LocalDateTime, effective: String, rows: List<MetricRow>): BandwidthSeriesDto {
        val points = rows.groupBy { it.bucket }.toSortedMap().map { (bucket, group) ->
            BandwidthPointDto(bucket.toString(), group.sumOf { it.rx }, group.sumOf { it.tx }, group.sumOf { it.avgDown }, group.sumOf { it.avgUp }, group.sumOf { it.p95Down }, group.sumOf { it.p95Up }, group.map { it.coverage }.averageOrZero())
        }
        return BandwidthSeriesDto(meta(from, to, effective, rows.map { it.coverage }), points)
    }

    @Transactional(readOnly = true)
    open fun subscriptions(from: LocalDateTime, to: LocalDateTime, routerId: Int?, planId: Int?, search: String?, sort: String, page: Int, size: Int): BandwidthSubscriptionPageDto {
        val effective = effectiveRankingResolution(from, to)
        val candidates = eligibleClients(routerId, planId).filter {
            it.customerName.contains(search.orEmpty(), true) ||
                it.ip.orEmpty().contains(search.orEmpty(), true) ||
                it.subscriptionId.toString() == search
        }
        val candidateIds = candidates.map { it.subscriptionId }.toSet()
        val rowsBySubscription = metricRows(from, to, effective, candidateIds).groupBy { it.subscriptionId }
        val items = candidates.map { client -> toSubscriptionRow(client, rowsBySubscription[client.subscriptionId].orEmpty()) }
        val sorted = when (sort) {
            "p95" -> items.sortedByDescending { it.p95MbpsDown }
            "utilization" -> items.sortedByDescending { it.utilizationPct ?: -1.0 }
            "coverage" -> items.sortedBy { it.coveragePct }
            else -> items.sortedByDescending { it.rxBytes + it.txBytes }
        }
        val safeSize = size.coerceIn(10, 100); val safePage = page.coerceAtLeast(0)
        val slice = sorted.drop(safePage * safeSize).take(safeSize)
        return BandwidthSubscriptionPageDto(meta(from, to, effective, items.map { it.coveragePct }), slice, safePage, safeSize, items.size.toLong(), ceil(items.size / safeSize.toDouble()).toInt())
    }

    @Transactional(readOnly = true)
    open fun subscriptionDetail(id: Int, from: LocalDateTime, to: LocalDateTime, resolution: String): BandwidthSubscriptionDetailDto? {
        val client = eligibleClients(null, null).firstOrNull { it.subscriptionId == id } ?: directoryTarget(id) ?: return null
        val effective = effectiveResolution(from, to, resolution, network = false)
        val rows = metricRows(from, to, effective, setOf(id))
        val series = seriesFromRows(from, to, effective, rows)
        return BandwidthSubscriptionDetailDto(toSubscriptionRow(client, rows), series, anomalies(id, from, to))
    }

    @Transactional(readOnly = true)
    open fun sources(): BandwidthSourcesDto {
        val names = routerRepository.findAll().associate { it.id to it.name }
        val latest = sourceRunRepository.findTop100ByOrderByStartedAtDesc().distinctBy { it.hostDeviceId }
        val aggregation = aggregationJobService.layerHealth().map {
            BandwidthAggregationLayerDto(it.layer.name, it.consolidatedThrough?.toString(), it.lagSeconds)
        }
        return BandwidthSourcesDto(
            LocalDateTime.now().toString(),
            latest.map { run ->
                val coverage = if (run.expectedCount == 0) 0.0 else run.writtenCount * 100.0 / run.expectedCount
                val lag = run.completedAt?.let { Duration.between(it, LocalDateTime.now()).seconds.coerceAtLeast(0) } ?: run.lagSeconds
                BandwidthSourceDto(run.hostDeviceId, names[run.hostDeviceId] ?: "Router ${run.hostDeviceId}", run.status.name, run.startedAt.toString(), run.completedAt?.toString(), run.durationMs, lag, run.expectedCount, run.matchedCount, run.writtenCount, run.missingCount, run.invalidCount, coverage.coerceIn(0.0, 100.0), run.errorMessage)
            },
            aggregation
        )
    }

    @Transactional(readOnly = true)
    open fun anomalyPage(type: TrafficAnomalyType?, status: TrafficAnomalyStatus?, subscriptionId: Int?, routerId: Int?, page: Int, size: Int): BandwidthAnomalyPageDto {
        val filtered = anomalyRepository.findFiltered(type, status, subscriptionId, routerId)
        val safeSize = size.coerceIn(10, 100); val safePage = page.coerceAtLeast(0)
        return BandwidthAnomalyPageDto(filtered.drop(safePage * safeSize).take(safeSize).map(::toAnomaly), safePage, safeSize, filtered.size.toLong(), ceil(filtered.size / safeSize.toDouble()).toInt())
    }

    override fun observations(subscriptionId: Int, from: LocalDateTime, to: LocalDateTime) = series(from, to, "auto", null, null, subscriptionId)
    override fun anomalies(subscriptionId: Int, from: LocalDateTime, to: LocalDateTime) = anomalyRepository.findBySubscriptionIdAndStartedAtBetweenOrderByStartedAtDesc(subscriptionId, from, to).map(::toAnomaly)

    private fun metricRows(from: LocalDateTime, to: LocalDateTime, resolution: String, ids: Set<Int>): List<MetricRow> {
        if (ids.isEmpty()) return emptyList()
        val singleId = ids.singleOrNull()
        return when (resolution) {
            "1m" -> (if (singleId != null) sampleRepository.findBySubscriptionIdAndBucketStartBetweenOrderByBucketStartAsc(singleId, from, to) else sampleRepository.findAllInBucketRange(from, to))
                .asSequence().filter { it.subscriptionId != null && it.subscriptionId in ids && it.sampleStatus == TrafficSampleStatus.OK }
                .map { MetricRow(it.subscriptionId!!, it.bucketStart, it.rxBytesDelta ?: 0, it.txBytesDelta ?: 0, it.avgMbpsDown ?: 0.0, it.avgMbpsUp ?: 0.0, it.avgMbpsDown ?: 0.0, it.avgMbpsUp ?: 0.0, 100.0) }.toList()
            "5m" -> (if (singleId != null) fiveMinuteRepository.findBySubscriptionIdAndBucketStartBetweenOrderByBucketStartAsc(singleId, from, to) else fiveMinuteRepository.findInBucketRangeForSubscriptions(from, to, ids))
                .map { MetricRow(it.subscriptionId, it.bucketStart, it.rxBytesTotal, it.txBytesTotal, it.avgMbpsDown, it.avgMbpsUp, it.p95MbpsDown, it.p95MbpsUp, it.coveragePct) }
            "1d" -> (if (singleId != null) dailyRepository.findBySubscriptionIdAndBucketStartBetweenOrderByBucketStartAsc(singleId, from.toLocalDate(), to.toLocalDate().plusDays(1)) else dailyRepository.findInBucketRangeForSubscriptions(from.toLocalDate(), to.toLocalDate().plusDays(1), ids))
                .map { MetricRow(it.subscriptionId, it.bucketStart.atStartOfDay(), it.rxBytesTotal, it.txBytesTotal, it.avgMbpsDown, it.avgMbpsUp, it.p95MbpsDown, it.p95MbpsUp, it.coveragePct) }
            else -> (if (singleId != null) hourlyRepository.findBySubscriptionIdAndBucketStartBetweenOrderByBucketStartAsc(singleId, from, to) else hourlyRepository.findInBucketRangeForSubscriptions(from, to, ids))
                .map { MetricRow(it.subscriptionId, it.bucketStart, it.rxBytesTotal, it.txBytesTotal, it.avgMbpsDown, it.avgMbpsUp, it.p95MbpsDown, it.p95MbpsUp, it.coveragePct) }
        }
    }

    private fun effectiveResolution(from: LocalDateTime, to: LocalDateTime, requested: String, network: Boolean): String {
        if (requested in setOf("1m", "5m", "1h", "1d")) return requested
        val hours = Duration.between(from, to).toHours()
        if (!network) return when { hours <= 24 -> "1m"; hours <= 24 * 2 -> "5m"; hours <= 24 * 90 -> "1h"; else -> "1d" }
        return when { hours <= 24 * 7 -> "1h"; else -> "1d" }
    }

    private fun effectiveRankingResolution(from: LocalDateTime, to: LocalDateTime): String {
        val hours = Duration.between(from, to).toHours()
        return when {
            hours <= 24 * 2 -> "5m"
            hours <= 24 * 7 -> "1h"
            else -> "1d"
        }
    }

    private data class EligibleClient(
        val subscriptionId: Int,
        val ip: String?,
        val routerId: Int?,
        val routerName: String?,
        val planId: Int?,
        val planName: String?,
        val planDownloadMbps: Int?,
        val planUploadMbps: Int?,
        val customerName: String,
    )

    private fun loadDirectory(): List<TrafficDirectoryTarget> = try {
        directory.list()
    } catch (_: Exception) {
        emptyList()
    }

    private fun eligibleClients(routerId: Int?, planId: Int?): List<EligibleClient> {
        val routers = routerRepository.findAll().associateBy { it.id }
        return loadDirectory()
            .filter { (routerId == null || it.routerHint == routerId) && (planId == null || it.planId == planId) }
            .map { it.toClient(routers[it.routerHint]?.name) }
    }

    private fun eligibleSubscriptionIds(routerId: Int?, planId: Int?) = eligibleClients(routerId, planId).map { it.subscriptionId }.toSet()

    private fun directoryTarget(id: Int): EligibleClient? {
        val routers = routerRepository.findAll().associateBy { it.id }
        val target = loadDirectory().firstOrNull { it.subscriptionId == id } ?: return null
        return target.toClient(routers[target.routerHint]?.name)
    }

    private fun TrafficDirectoryTarget.toClient(routerName: String?) = EligibleClient(
        subscriptionId = subscriptionId,
        ip = ip,
        routerId = routerHint,
        routerName = routerName,
        planId = planId,
        planName = planName,
        planDownloadMbps = planDownloadMbps,
        planUploadMbps = planUploadMbps,
        customerName = displayName?.takeIf { it.isNotBlank() } ?: "Suscripción $subscriptionId",
    )

    private fun toSubscriptionRow(s: EligibleClient, rows: List<MetricRow>): BandwidthSubscriptionRowDto {
        val coverage = rows.map { it.coverage }.averageOrZero(); val p95Down = percentile(rows.map { it.avgDown }, .95); val planDown = s.planDownloadMbps
        return BandwidthSubscriptionRowDto(s.subscriptionId, s.customerName, s.ip, s.routerId, s.routerName, s.planId, s.planName, planDown, s.planUploadMbps, rows.sumOf { it.rx }, rows.sumOf { it.tx }, p95Down, percentile(rows.map { it.avgUp }, .95), planDown?.takeIf { it > 0 }?.let { p95Down * 100.0 / it }, coverage, quality(coverage))
    }
    private fun toSubscriptionRow(s: EligibleClient, summary: SubscriptionTrafficRawSummaryProjection?): BandwidthSubscriptionRowDto {
        val coverage = if (summary == null || summary.getSampleCount() == 0) 0.0 else 100.0
        val p95Down = summary?.getP95MbpsDown() ?: 0.0
        val planDown = s.planDownloadMbps
        return BandwidthSubscriptionRowDto(s.subscriptionId, s.customerName, s.ip, s.routerId, s.routerName, s.planId, s.planName, planDown, s.planUploadMbps, summary?.getRxBytes() ?: 0, summary?.getTxBytes() ?: 0, p95Down, summary?.getP95MbpsUp() ?: 0.0, planDown?.takeIf { it > 0 }?.let { p95Down * 100.0 / it }, coverage, quality(coverage))
    }
    private fun meta(from: LocalDateTime, to: LocalDateTime, resolution: String, coverages: List<Double>): BandwidthRangeDto { val coverage = coverages.averageOrZero(); return BandwidthRangeDto(from.toString(), to.toString(), resolution, coverage, toStringFreshness(to), quality(coverage)) }
    private fun toStringFreshness(to: LocalDateTime) = if (Duration.between(to, LocalDateTime.now()).toMinutes() <= 15) "FRESH" else "STALE"
    private fun quality(coverage: Double) = when { coverage >= 90 -> "GOOD"; coverage >= 80 -> "PARTIAL"; else -> "POOR" }
    private fun growthFromPoints(points: List<BandwidthPointDto>): Double? {
        if (points.size < 2) return null
        val half = points.size / 2
        val a = points.take(half).sumOf { it.rxBytes + it.txBytes }.toDouble()
        val b = points.drop(half).sumOf { it.rxBytes + it.txBytes }.toDouble()
        return if (a == 0.0) null else (b - a) * 100.0 / a
    }
    private fun percentile(values: List<Double>, p: Double): Double { if (values.isEmpty()) return 0.0; val sorted = values.sorted(); return sorted[(ceil(p * sorted.size).toInt().coerceIn(1, sorted.size) - 1)] }
    private fun List<Double>.averageOrZero() = if (isEmpty()) 0.0 else average()
    private fun toAnomaly(e: TrafficAnomalyEvent) = BandwidthAnomalyDto(requireNotNull(e.id), e.anomalyType.name, e.eventStatus.name, e.subscriptionId, e.hostDeviceId, e.startedAt.toString(), e.endedAt?.toString(), e.baselineValue, e.observedValue, e.deviationValue, e.coveragePct, e.confidence, e.ruleVersion, e.evidenceJson)
}
