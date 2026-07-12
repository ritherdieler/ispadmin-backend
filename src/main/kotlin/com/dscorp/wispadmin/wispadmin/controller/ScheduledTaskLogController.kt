package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.ScheduledTaskType
import com.dscorp.wispadmin.wispadmin.dto.ScheduledTaskLogDto
import com.dscorp.wispadmin.wispadmin.dto.toDto
import com.dscorp.wispadmin.wispadmin.service.ScheduledTaskLogService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/scheduled-task-logs")
class ScheduledTaskLogController(
    private val scheduledTaskLogService: ScheduledTaskLogService
) {

    @GetMapping("/recent")
    fun getRecentLogs(
        @RequestParam(defaultValue = "50") limit: Int
    ): ResponseEntity<List<ScheduledTaskLogDto>> {
        val logs = scheduledTaskLogService.getRecentLogs(limit).map { it.toDto() }
        return ResponseEntity.ok(logs)
    }

    @GetMapping("/by-task-type/{taskType}")
    fun getLogsByTaskType(
        @PathVariable taskType: ScheduledTaskType
    ): ResponseEntity<List<ScheduledTaskLogDto>> {
        val logs = scheduledTaskLogService.getLogsByTaskType(taskType).map { it.toDto() }
        return ResponseEntity.ok(logs)
    }

    @GetMapping("/by-date-range")
    fun getLogsByDateRange(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startDate: LocalDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endDate: LocalDateTime
    ): ResponseEntity<List<ScheduledTaskLogDto>> {
        val logs = scheduledTaskLogService.getLogsByDateRange(startDate, endDate).map { it.toDto() }
        return ResponseEntity.ok(logs)
    }

    @GetMapping("/task-types")
    fun getTaskTypes(): ResponseEntity<List<Map<String, String>>> {
        val taskTypes = ScheduledTaskType.values().map { 
            mapOf(
                "value" to it.name,
                "label" to getTaskTypeName(it)
            )
        }
        return ResponseEntity.ok(taskTypes)
    }

    private fun getTaskTypeName(taskType: ScheduledTaskType): String {
        return when (taskType) {
            ScheduledTaskType.CUT_INTERNET_SERVICE -> "Corte de Servicio de Internet"
            ScheduledTaskType.CUT_INTERNET_SERVICE_DEBTORS -> "Corte de Servicio - Deudores"
            ScheduledTaskType.CUT_INTERNET_SERVICE_CANCELLED -> "Corte de Servicio - Cancelados"
            ScheduledTaskType.GENERATE_ADDRESS_LIST_CANCELLED -> "Generación de Address List (Cancelados)"
            ScheduledTaskType.CREATE_SUBSCRIPTIONS_QUEUE -> "Creación de Queues de Suscripciones"
        }
    }
}

