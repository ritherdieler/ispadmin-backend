package com.dscorp.wispadmin.traffic.controller

import com.dscorp.wispadmin.traffic.config.TrafficProperties
import com.dscorp.wispadmin.traffic.dto.SubscriptionTrafficDayDto
import com.dscorp.wispadmin.traffic.dto.SubscriptionTrafficLatestDto
import com.dscorp.wispadmin.traffic.dto.SubscriptionTrafficSeriesDto
import com.dscorp.wispadmin.traffic.dto.SubscriptionTrafficSummaryDto
import com.dscorp.wispadmin.traffic.entity.TrafficAnomalyStatus
import com.dscorp.wispadmin.traffic.entity.TrafficAnomalyType
import com.dscorp.wispadmin.traffic.repository.TrafficAnomalyEventRepository
import com.dscorp.wispadmin.traffic.repository.TrafficSourceRunRepository
import com.dscorp.wispadmin.traffic.service.BandwidthIntelligenceService
import com.dscorp.wispadmin.traffic.service.NetworkTrafficAnalyticsService
import com.dscorp.wispadmin.traffic.service.SubscriptionTrafficPollService
import com.dscorp.wispadmin.traffic.service.SubscriptionTrafficQueryService
import com.dscorp.wispadmin.traffic.service.TrafficAggregationJobService
import org.springframework.data.domain.PageRequest
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/traffic/v1")
class TrafficApiController(
    private val queryService: SubscriptionTrafficQueryService,
    private val bandwidthService: BandwidthIntelligenceService,
    private val networkAnalytics: NetworkTrafficAnalyticsService,
    private val pollService: SubscriptionTrafficPollService,
    private val aggregationJobService: TrafficAggregationJobService,
    private val sourceRunRepository: TrafficSourceRunRepository,
    private val anomalyRepository: TrafficAnomalyEventRepository,
    private val properties: TrafficProperties,
) {

    @GetMapping("/by-subscription/{id}/latest")
    fun latestBySubscription(@PathVariable id: Int): SubscriptionTrafficLatestDto = queryService.getLatest(id)

    @GetMapping("/by-subscription/{id}/series")
    fun seriesBySubscription(
        @PathVariable id: Int,
        @RequestParam(defaultValue = "sample") granularity: String,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime?,
    ): SubscriptionTrafficSeriesDto = queryService.getSeries(id, granularity, from, to)

    @GetMapping("/by-subscription/{id}/summary")
    fun summaryBySubscription(
        @PathVariable id: Int,
        @RequestParam(required = false) month: String?,
    ): SubscriptionTrafficSummaryDto = queryService.getSummary(id, month)

    @GetMapping("/by-subscription/{id}/today")
    fun todayBySubscription(@PathVariable id: Int): SubscriptionTrafficDayDto = queryService.getToday(id)

    @GetMapping("/by-subscription/{id}/day")
    fun dayBySubscription(
        @PathVariable id: Int,
        @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
    ): SubscriptionTrafficDayDto = queryService.getDay(id, date)

    @GetMapping("/by-ip/{ip}/latest")
    fun latestByIp(@PathVariable ip: String): SubscriptionTrafficLatestDto = queryService.getLatestByIp(ip)

    @GetMapping("/by-ip/{ip}/series")
    fun seriesByIp(
        @PathVariable ip: String,
        @RequestParam(defaultValue = "sample") granularity: String,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime?,
    ): SubscriptionTrafficSeriesDto = queryService.getSeriesByIp(ip, granularity, from, to)

    @GetMapping("/network")
    fun network(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime,
        @RequestParam(defaultValue = "auto") resolution: String,
        @RequestParam(required = false) routerId: Int?,
        @RequestParam(required = false) planId: Int?,
    ) = bandwidthService.network(from, to, resolution, routerId, planId)

    @GetMapping("/overview")
    fun overview(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime,
        @RequestParam(defaultValue = "auto") resolution: String,
        @RequestParam(required = false) routerId: Int?,
        @RequestParam(required = false) planId: Int?,
    ) = bandwidthService.overview(from, to, resolution, routerId, planId)

    @GetMapping("/series")
    fun series(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime,
        @RequestParam(defaultValue = "auto") resolution: String,
        @RequestParam(required = false) routerId: Int?,
        @RequestParam(required = false) planId: Int?,
    ) = bandwidthService.series(from, to, resolution, routerId, planId)

    @GetMapping("/sources")
    fun sources() = bandwidthService.sources()

    @GetMapping("/subscriptions")
    fun subscriptions(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime,
        @RequestParam(required = false) routerId: Int?,
        @RequestParam(required = false) planId: Int?,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "consumption") sort: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ) = bandwidthService.subscriptions(from, to, routerId, planId, search, sort, page, size)

    @GetMapping("/subscriptions/{id}")
    fun subscriptionDetail(
        @PathVariable id: Int,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime,
        @RequestParam(defaultValue = "auto") resolution: String,
    ) = bandwidthService.subscriptionDetail(id, from, to, resolution)

    @GetMapping("/network/hourly-profile")
    fun hourlyProfile(@RequestParam(defaultValue = "3") months: Int) = networkAnalytics.getHourlyProfile(months)

    @GetMapping("/network/daily-trend")
    fun dailyTrend(@RequestParam(defaultValue = "3") months: Int) = networkAnalytics.getDailyTrend(months)

    @GetMapping("/network/insights")
    fun insights(@RequestParam(defaultValue = "3") months: Int) = networkAnalytics.getInsights(months)

    @GetMapping("/anomalies")
    fun anomalies(
        @RequestParam(required = false) type: TrafficAnomalyType?,
        @RequestParam(required = false) status: TrafficAnomalyStatus?,
        @RequestParam(required = false) subscriptionId: Int?,
        @RequestParam(required = false) routerId: Int?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ) = bandwidthService.anomalyPage(type, status, subscriptionId, routerId, page, size)

    @GetMapping("/routers/{id}/latest-run")
    fun latestRun(@PathVariable id: Int): Map<String, Any?> {
        val run = sourceRunRepository.findTopByHostDeviceIdOrderByStartedAtDesc(id)
        return mapOf(
            "id" to run?.id,
            "completedAt" to run?.completedAt?.toString(),
            "status" to run?.status?.name,
        )
    }

    @GetMapping("/anomalies/changes")
    fun anomalyChanges(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) after: LocalDateTime,
        @RequestParam(defaultValue = "0") afterId: Long,
        @RequestParam(defaultValue = "50") size: Int,
    ): List<Map<String, Any?>> {
        return anomalyRepository.findChanges(after, afterId, PageRequest.of(0, size.coerceIn(1, 200))).map { event ->
            mapOf(
                "id" to event.id,
                "subscriptionId" to event.subscriptionId,
                "hostDeviceId" to event.hostDeviceId,
                "eventStatus" to event.eventStatus.name,
                "anomalyType" to event.anomalyType.name,
                "lastEvaluatedAt" to event.lastEvaluatedAt.toString(),
                "coveragePct" to event.coveragePct,
                "confidence" to event.confidence,
                "evidenceJson" to event.evidenceJson,
            )
        }
    }

    @GetMapping("/config")
    fun config(): Map<String, Any> = mapOf("minimumCoveragePct" to properties.anomaly.minimumCoveragePct)

    @PostMapping("/admin/poll")
    fun pollNow() = pollService.pollTraffic()

    @PostMapping("/admin/aggregation/catch-up")
    fun catchUpNow() = mapOf(
        "fiveMinute" to aggregationJobService.catchUpFiveMinute(),
        "hourly" to aggregationJobService.catchUpHourly(),
        "daily" to aggregationJobService.catchUpDaily(),
    )
}
