package com.dscorp.wispadmin.observability.controller

import com.dscorp.wispadmin.observability.dto.CreateDeployEventRequest
import com.dscorp.wispadmin.observability.dto.DeployEventDto
import com.dscorp.wispadmin.observability.service.ObsDeployEventService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/observability/releases")
class ObservabilityReleaseController(
    private val deployEventService: ObsDeployEventService
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) platform: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime?
    ): List<DeployEventDto> {
        return deployEventService.list(platform, from, to)
    }

    @PostMapping
    fun create(@RequestBody request: CreateDeployEventRequest): ResponseEntity<DeployEventDto> {
        val created = deployEventService.create(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }
}
