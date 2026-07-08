package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.Modules
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.dto.SmartMapSummaryDto
import com.dscorp.wispadmin.wispadmin.extensions.toErrorLog
import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import com.dscorp.wispadmin.wispadmin.service.SmartMapService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/smart-map")
class SmartMapController(
    private val smartMapService: SmartMapService,
    private val errorLogRepository: ErrorLogRepository
) {

    @GetMapping("/summary")
    fun getSummary(
        @RequestParam(required = false, defaultValue = "true") includeDebt: Boolean,
        @RequestParam(required = false, defaultValue = "true") includeTickets: Boolean,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) serviceStatuses: List<ServiceStatus>?,
        @RequestParam(required = false) installationType: String?,
        @RequestParam(required = false) place: String?,
        @RequestParam(required = false) plan: String?,
        @RequestParam(required = false, defaultValue = "false") onlyWithDebt: Boolean,
        @RequestParam(required = false) startDate: Long? = null,
        @RequestParam(required = false) endDate: Long? = null,
    ): ResponseEntity<SmartMapSummaryDto> {
        return try {
            ResponseEntity.ok(
                smartMapService.getSummary(
                    includeDebt = includeDebt,
                    includeTickets = includeTickets,
                    search = search,
                    serviceStatuses = serviceStatuses,
                    installationType = installationType,
                    place = place,
                    plan = plan,
                    onlyWithDebt = onlyWithDebt,
                    startDate = startDate,
                    endDate = endDate,
                )
            )
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            e.printStackTrace()
            ResponseEntity.status(500).body(null)
        }
    }
}
