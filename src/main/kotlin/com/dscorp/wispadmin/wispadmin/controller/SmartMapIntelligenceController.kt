package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.Modules
import com.dscorp.wispadmin.wispadmin.dto.CommercialOpportunityDto
import com.dscorp.wispadmin.wispadmin.dto.CoverageZoneDto
import com.dscorp.wispadmin.wispadmin.dto.SalesLeadMapDto
import com.dscorp.wispadmin.wispadmin.dto.toDto
import com.dscorp.wispadmin.wispadmin.extensions.toErrorLog
import com.dscorp.wispadmin.wispadmin.repository.CommercialOpportunityRepository
import com.dscorp.wispadmin.wispadmin.repository.CoverageZoneRepository
import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import com.dscorp.wispadmin.wispadmin.repository.SalesLeadMapRepository
import com.dscorp.wispadmin.wispadmin.requestbody.CommercialOpportunityRequest
import com.dscorp.wispadmin.wispadmin.requestbody.CoverageZoneRequest
import com.dscorp.wispadmin.wispadmin.requestbody.SalesLeadMapRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/smart-map")
class SmartMapIntelligenceController(
    private val coverageZoneRepository: CoverageZoneRepository,
    private val salesLeadMapRepository: SalesLeadMapRepository,
    private val commercialOpportunityRepository: CommercialOpportunityRepository,
    private val errorLogRepository: ErrorLogRepository,
) {

    @GetMapping("/coverage-zones")
    fun getCoverageZones(): ResponseEntity<List<CoverageZoneDto>> {
        return try {
            ResponseEntity.ok(coverageZoneRepository.findAll().map { it.toDto() })
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            ResponseEntity.status(500).body(emptyList())
        }
    }

    @PostMapping("/coverage-zones")
    fun createCoverageZone(@RequestBody request: CoverageZoneRequest): ResponseEntity<CoverageZoneDto> {
        return try {
            ResponseEntity.ok(coverageZoneRepository.save(request.toEntity()).toDto())
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            ResponseEntity.status(500).body(null)
        }
    }

    @PutMapping("/coverage-zones/{id}")
    fun updateCoverageZone(
        @PathVariable id: Int,
        @RequestBody request: CoverageZoneRequest,
    ): ResponseEntity<CoverageZoneDto> {
        return try {
            val existing = coverageZoneRepository.findById(id).orElse(null)
                ?: return ResponseEntity.notFound().build()
            val updated = existing.copy(
                name = request.name.trim(),
                coverageType = request.coverageType,
                status = request.status,
                latitude = request.latitude,
                longitude = request.longitude,
                geometryGeoJson = request.geometryGeoJson?.trim()?.takeIf { it.isNotEmpty() },
                notes = request.notes?.trim(),
            )
            ResponseEntity.ok(coverageZoneRepository.save(updated).toDto())
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            ResponseEntity.status(500).body(null)
        }
    }

    @DeleteMapping("/coverage-zones/{id}")
    fun deleteCoverageZone(@PathVariable id: Int): ResponseEntity<Void> {
        return try {
            if (!coverageZoneRepository.existsById(id)) {
                return ResponseEntity.notFound().build()
            }
            coverageZoneRepository.deleteById(id)
            ResponseEntity.ok().build()
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            ResponseEntity.status(500).build()
        }
    }

    @GetMapping("/sales-leads")
    fun getSalesLeads(): ResponseEntity<List<SalesLeadMapDto>> {
        return try {
            ResponseEntity.ok(salesLeadMapRepository.findAll().map { it.toDto() })
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            ResponseEntity.status(500).body(emptyList())
        }
    }

    @PostMapping("/sales-leads")
    fun createSalesLead(@RequestBody request: SalesLeadMapRequest): ResponseEntity<SalesLeadMapDto> {
        return try {
            ResponseEntity.ok(salesLeadMapRepository.save(request.toEntity()).toDto())
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            ResponseEntity.status(500).body(null)
        }
    }

    @PutMapping("/sales-leads/{id}")
    fun updateSalesLead(
        @PathVariable id: Int,
        @RequestBody request: SalesLeadMapRequest,
    ): ResponseEntity<SalesLeadMapDto> {
        return try {
            val existing = salesLeadMapRepository.findById(id).orElse(null)
                ?: return ResponseEntity.notFound().build()
            val updated = existing.copy(
                referenceName = request.referenceName.trim(),
                phone = request.phone?.trim(),
                sector = request.sector?.trim(),
                latitude = request.latitude,
                longitude = request.longitude,
                source = request.source?.trim(),
                leadStatus = request.leadStatus,
                notes = request.notes?.trim(),
            )
            ResponseEntity.ok(salesLeadMapRepository.save(updated).toDto())
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            ResponseEntity.status(500).body(null)
        }
    }

    @DeleteMapping("/sales-leads/{id}")
    fun deleteSalesLead(@PathVariable id: Int): ResponseEntity<Void> {
        return try {
            if (!salesLeadMapRepository.existsById(id)) {
                return ResponseEntity.notFound().build()
            }
            salesLeadMapRepository.deleteById(id)
            ResponseEntity.ok().build()
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            ResponseEntity.status(500).build()
        }
    }

    @GetMapping("/commercial-opportunities")
    fun getCommercialOpportunities(): ResponseEntity<List<CommercialOpportunityDto>> {
        return try {
            ResponseEntity.ok(commercialOpportunityRepository.findAll().map { it.toDto() })
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            ResponseEntity.status(500).body(emptyList())
        }
    }

    @PostMapping("/commercial-opportunities")
    fun createCommercialOpportunity(
        @RequestBody request: CommercialOpportunityRequest,
    ): ResponseEntity<CommercialOpportunityDto> {
        return try {
            ResponseEntity.ok(commercialOpportunityRepository.save(request.toEntity()).toDto())
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            ResponseEntity.status(500).body(null)
        }
    }

    @PutMapping("/commercial-opportunities/{id}")
    fun updateCommercialOpportunity(
        @PathVariable id: Int,
        @RequestBody request: CommercialOpportunityRequest,
    ): ResponseEntity<CommercialOpportunityDto> {
        return try {
            val existing = commercialOpportunityRepository.findById(id).orElse(null)
                ?: return ResponseEntity.notFound().build()
            val updated = existing.copy(
                zoneName = request.zoneName.trim(),
                priority = request.priority,
                reason = request.reason?.trim(),
                estimatedClients = request.estimatedClients.coerceAtLeast(0),
                evaluationStatus = request.evaluationStatus,
                latitude = request.latitude,
                longitude = request.longitude,
                notes = request.notes?.trim(),
            )
            ResponseEntity.ok(commercialOpportunityRepository.save(updated).toDto())
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            ResponseEntity.status(500).body(null)
        }
    }

    @DeleteMapping("/commercial-opportunities/{id}")
    fun deleteCommercialOpportunity(@PathVariable id: Int): ResponseEntity<Void> {
        return try {
            if (!commercialOpportunityRepository.existsById(id)) {
                return ResponseEntity.notFound().build()
            }
            commercialOpportunityRepository.deleteById(id)
            ResponseEntity.ok().build()
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            ResponseEntity.status(500).build()
        }
    }
}
