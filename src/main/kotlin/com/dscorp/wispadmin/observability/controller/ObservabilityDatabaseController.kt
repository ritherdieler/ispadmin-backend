package com.dscorp.wispadmin.observability.controller

import com.dscorp.wispadmin.observability.dto.DbQueryAggregateDto
import com.dscorp.wispadmin.observability.dto.NPlusOneCandidateDto
import com.dscorp.wispadmin.observability.service.ObsDatabaseQueryService
import org.springframework.beans.factory.annotation.Value
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.ZoneId

@RestController
@RequestMapping("/observability/database")
class ObservabilityDatabaseController(
    private val databaseQueryService: ObsDatabaseQueryService,
    @Value("\${app.timezone:America/Lima}") private val appTimezone: String
) {

    @GetMapping("/queries")
    fun queries(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime?,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(required = false) release: String?
    ): List<DbQueryAggregateDto> {
        val zone = ZoneId.of(appTimezone)
        val toDate = to ?: LocalDateTime.now()
        val fromDate = from ?: toDate.minusHours(1)
        return databaseQueryService.topQueries(
            fromDate.atZone(zone).toInstant().toEpochMilli(),
            toDate.atZone(zone).toInstant().toEpochMilli(),
            limit,
            release
        )
    }

    @GetMapping("/nplusone")
    fun nplusone(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime?,
        @RequestParam(defaultValue = "5") threshold: Long,
        @RequestParam(required = false) release: String?
    ): List<NPlusOneCandidateDto> {
        val zone = ZoneId.of(appTimezone)
        val toDate = to ?: LocalDateTime.now()
        val fromDate = from ?: toDate.minusHours(1)
        return databaseQueryService.nPlusOne(
            fromDate.atZone(zone).toInstant().toEpochMilli(),
            toDate.atZone(zone).toInstant().toEpochMilli(),
            threshold,
            release
        )
    }
}
