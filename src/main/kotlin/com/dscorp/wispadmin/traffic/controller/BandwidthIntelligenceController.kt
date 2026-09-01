package com.dscorp.wispadmin.traffic.controller

import com.dscorp.wispadmin.traffic.dto.*
import com.dscorp.wispadmin.traffic.entity.TrafficAnomalyStatus
import com.dscorp.wispadmin.traffic.entity.TrafficAnomalyType
import com.dscorp.wispadmin.traffic.service.BandwidthIntelligenceService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime

@RestController
@RequestMapping("/traffic/bandwidth/v1")
class BandwidthIntelligenceController(private val service: BandwidthIntelligenceService) {
    @GetMapping("/network") fun network(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime, @RequestParam(defaultValue = "auto") resolution: String, @RequestParam(required = false) routerId: Int?, @RequestParam(required = false) planId: Int?) = service.network(from, to, resolution, routerId, planId)
    @GetMapping("/overview") fun overview(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime, @RequestParam(defaultValue = "auto") resolution: String, @RequestParam(required = false) routerId: Int?, @RequestParam(required = false) planId: Int?) = service.overview(from, to, resolution, routerId, planId)
    @GetMapping("/series") fun series(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime, @RequestParam(defaultValue = "auto") resolution: String, @RequestParam(required = false) routerId: Int?, @RequestParam(required = false) planId: Int?) = service.series(from, to, resolution, routerId, planId)
    @GetMapping("/subscriptions") fun subscriptions(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime, @RequestParam(required = false) routerId: Int?, @RequestParam(required = false) planId: Int?, @RequestParam(required = false) search: String?, @RequestParam(defaultValue = "consumption") sort: String, @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "25") size: Int) = service.subscriptions(from, to, routerId, planId, search, sort, page, size)
    @GetMapping("/subscriptions/{id}") fun subscription(@PathVariable id: Int, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime, @RequestParam(defaultValue = "auto") resolution: String) = service.subscriptionDetail(id, from, to, resolution) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found")
    @GetMapping("/sources") fun sources() = service.sources()
    @GetMapping("/anomalies") fun anomalies(@RequestParam(required = false) type: TrafficAnomalyType?, @RequestParam(required = false) status: TrafficAnomalyStatus?, @RequestParam(required = false) subscriptionId: Int?, @RequestParam(required = false) routerId: Int?, @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "25") size: Int) = service.anomalyPage(type, status, subscriptionId, routerId, page, size)
}
