package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.dto.CommercialOpportunityDto
import com.dscorp.wispadmin.wispadmin.dto.CoverageZoneDto
import com.dscorp.wispadmin.wispadmin.dto.SalesLeadMapDto
import com.dscorp.wispadmin.wispadmin.requestbody.CommercialOpportunityRequest
import com.dscorp.wispadmin.wispadmin.requestbody.CoverageZoneRequest
import com.dscorp.wispadmin.wispadmin.requestbody.SalesLeadMapRequest
import com.dscorp.wispadmin.wispadmin.smartmap.SmartMapIntelligenceService
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
import javax.validation.Valid

@RestController
@RequestMapping("/smart-map")
class SmartMapIntelligenceController(
    private val smartMapIntelligenceService: SmartMapIntelligenceService,
) {

    @GetMapping("/coverage-zones")
    fun getCoverageZones(): ResponseEntity<List<CoverageZoneDto>> =
        ResponseEntity.ok(smartMapIntelligenceService.listCoverageZones())

    @PostMapping("/coverage-zones")
    fun createCoverageZone(
        @Valid @RequestBody request: CoverageZoneRequest,
        @RequestParam(required = false) userType: String?,
    ): ResponseEntity<CoverageZoneDto> =
        ResponseEntity.ok(smartMapIntelligenceService.createCoverageZone(request, userType))

    @PutMapping("/coverage-zones/{id}")
    fun updateCoverageZone(
        @PathVariable id: Int,
        @Valid @RequestBody request: CoverageZoneRequest,
        @RequestParam(required = false) userType: String?,
    ): ResponseEntity<CoverageZoneDto> =
        ResponseEntity.ok(smartMapIntelligenceService.updateCoverageZone(id, request, userType))

    @DeleteMapping("/coverage-zones/{id}")
    fun deleteCoverageZone(
        @PathVariable id: Int,
        @RequestParam(required = false) userType: String?,
    ): ResponseEntity<Void> {
        smartMapIntelligenceService.deleteCoverageZone(id, userType)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/sales-leads")
    fun getSalesLeads(): ResponseEntity<List<SalesLeadMapDto>> =
        ResponseEntity.ok(smartMapIntelligenceService.listSalesLeads())

    @PostMapping("/sales-leads")
    fun createSalesLead(
        @Valid @RequestBody request: SalesLeadMapRequest,
        @RequestParam(required = false) userType: String?,
    ): ResponseEntity<SalesLeadMapDto> =
        ResponseEntity.ok(smartMapIntelligenceService.createSalesLead(request, userType))

    @PutMapping("/sales-leads/{id}")
    fun updateSalesLead(
        @PathVariable id: Int,
        @Valid @RequestBody request: SalesLeadMapRequest,
        @RequestParam(required = false) userType: String?,
    ): ResponseEntity<SalesLeadMapDto> =
        ResponseEntity.ok(smartMapIntelligenceService.updateSalesLead(id, request, userType))

    @DeleteMapping("/sales-leads/{id}")
    fun deleteSalesLead(
        @PathVariable id: Int,
        @RequestParam(required = false) userType: String?,
    ): ResponseEntity<Void> {
        smartMapIntelligenceService.deleteSalesLead(id, userType)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/commercial-opportunities")
    fun getCommercialOpportunities(): ResponseEntity<List<CommercialOpportunityDto>> =
        ResponseEntity.ok(smartMapIntelligenceService.listCommercialOpportunities())

    @PostMapping("/commercial-opportunities")
    fun createCommercialOpportunity(
        @Valid @RequestBody request: CommercialOpportunityRequest,
        @RequestParam(required = false) userType: String?,
    ): ResponseEntity<CommercialOpportunityDto> =
        ResponseEntity.ok(smartMapIntelligenceService.createCommercialOpportunity(request, userType))

    @PutMapping("/commercial-opportunities/{id}")
    fun updateCommercialOpportunity(
        @PathVariable id: Int,
        @Valid @RequestBody request: CommercialOpportunityRequest,
        @RequestParam(required = false) userType: String?,
    ): ResponseEntity<CommercialOpportunityDto> =
        ResponseEntity.ok(smartMapIntelligenceService.updateCommercialOpportunity(id, request, userType))

    @DeleteMapping("/commercial-opportunities/{id}")
    fun deleteCommercialOpportunity(
        @PathVariable id: Int,
        @RequestParam(required = false) userType: String?,
    ): ResponseEntity<Void> {
        smartMapIntelligenceService.deleteCommercialOpportunity(id, userType)
        return ResponseEntity.ok().build()
    }
}
