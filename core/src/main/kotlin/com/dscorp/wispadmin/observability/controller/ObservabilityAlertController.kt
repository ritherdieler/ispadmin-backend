package com.dscorp.wispadmin.observability.controller

import com.dscorp.wispadmin.observability.dto.AlertChannelDto
import com.dscorp.wispadmin.observability.dto.AlertChannelUpsertRequest
import com.dscorp.wispadmin.observability.dto.AlertEventDto
import com.dscorp.wispadmin.observability.dto.AlertRuleDto
import com.dscorp.wispadmin.observability.dto.AlertRuleUpsertRequest
import com.dscorp.wispadmin.observability.dto.PagedResponse
import com.dscorp.wispadmin.observability.dto.TestChannelResult
import com.dscorp.wispadmin.observability.service.ObsAlertService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/observability/alerts")
class ObservabilityAlertController(
    private val alertService: ObsAlertService
) {

    @GetMapping("/rules")
    fun listRules(): List<AlertRuleDto> = alertService.listRules()

    @PostMapping("/rules")
    fun createRule(@RequestBody request: AlertRuleUpsertRequest): ResponseEntity<Any> =
        try {
            ResponseEntity.ok(alertService.createRule(request))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to e.message))
        }

    @PutMapping("/rules/{id}")
    fun updateRule(
        @PathVariable id: Long,
        @RequestBody request: AlertRuleUpsertRequest
    ): ResponseEntity<AlertRuleDto> {
        val updated = alertService.updateRule(id, request) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(updated)
    }

    @DeleteMapping("/rules/{id}")
    fun deleteRule(@PathVariable id: Long): ResponseEntity<Void> =
        if (alertService.deleteRule(id)) ResponseEntity.noContent().build()
        else ResponseEntity.notFound().build()

    @GetMapping("/channels")
    fun listChannels(): List<AlertChannelDto> = alertService.listChannels()

    @PostMapping("/channels")
    fun createChannel(@RequestBody request: AlertChannelUpsertRequest): ResponseEntity<Any> =
        try {
            ResponseEntity.ok(alertService.createChannel(request))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to e.message))
        }

    @PutMapping("/channels/{id}")
    fun updateChannel(
        @PathVariable id: Long,
        @RequestBody request: AlertChannelUpsertRequest
    ): ResponseEntity<AlertChannelDto> {
        val updated = alertService.updateChannel(id, request) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(updated)
    }

    @DeleteMapping("/channels/{id}")
    fun deleteChannel(@PathVariable id: Long): ResponseEntity<Void> =
        if (alertService.deleteChannel(id)) ResponseEntity.noContent().build()
        else ResponseEntity.notFound().build()

    @PostMapping("/channels/{id}/test")
    fun testChannel(@PathVariable id: Long): ResponseEntity<TestChannelResult> {
        val result = alertService.testChannel(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(result)
    }

    @GetMapping("/events")
    fun listEvents(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int
    ): PagedResponse<AlertEventDto> = alertService.listEvents(page, size)
}
