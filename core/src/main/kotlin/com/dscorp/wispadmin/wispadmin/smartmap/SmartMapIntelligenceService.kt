package com.dscorp.wispadmin.wispadmin.smartmap

import com.dscorp.wispadmin.wispadmin.config.SmartMapCacheConfiguration
import com.dscorp.wispadmin.wispadmin.dto.CommercialOpportunityDto
import com.dscorp.wispadmin.wispadmin.dto.CoverageZoneDto
import com.dscorp.wispadmin.wispadmin.dto.SalesLeadMapDto
import com.dscorp.wispadmin.wispadmin.dto.toDto
import com.dscorp.wispadmin.wispadmin.repository.CommercialOpportunityRepository
import com.dscorp.wispadmin.wispadmin.repository.CoverageZoneRepository
import com.dscorp.wispadmin.wispadmin.repository.SalesLeadMapRepository
import com.dscorp.wispadmin.wispadmin.requestbody.CommercialOpportunityRequest
import com.dscorp.wispadmin.wispadmin.requestbody.CoverageZoneRequest
import com.dscorp.wispadmin.wispadmin.requestbody.SalesLeadMapRequest
import org.springframework.cache.annotation.CacheEvict
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class SmartMapIntelligenceService(
    private val coverageZoneRepository: CoverageZoneRepository,
    private val salesLeadMapRepository: SalesLeadMapRepository,
    private val commercialOpportunityRepository: CommercialOpportunityRepository,
) {

    @Transactional(readOnly = true)
    fun listCoverageZones(): List<CoverageZoneDto> =
        coverageZoneRepository.findAll().map { it.toDto() }

    @Transactional
    @CacheEvict(
        cacheNames = [
            SmartMapCacheConfiguration.SMART_MAP_SUMMARY_CACHE,
            SmartMapCacheConfiguration.SMART_MAP_SUGGESTIONS_CACHE,
        ],
        allEntries = true,
    )
    fun createCoverageZone(request: CoverageZoneRequest, userType: String?): CoverageZoneDto {
        requireIntelligenceManager(userType)
        return coverageZoneRepository.save(request.toEntity()).toDto()
    }

    @Transactional
    @CacheEvict(
        cacheNames = [
            SmartMapCacheConfiguration.SMART_MAP_SUMMARY_CACHE,
            SmartMapCacheConfiguration.SMART_MAP_SUGGESTIONS_CACHE,
        ],
        allEntries = true,
    )
    fun updateCoverageZone(id: Int, request: CoverageZoneRequest, userType: String?): CoverageZoneDto {
        requireIntelligenceManager(userType)
        val existing = coverageZoneRepository.findById(id).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val updated = existing.copy(
            name = request.name.trim(),
            coverageType = request.coverageType,
            status = request.status,
            latitude = request.latitude,
            longitude = request.longitude,
            notes = request.notes?.trim(),
            geometryGeoJson = request.geometryGeoJson?.trim()?.takeIf { it.isNotEmpty() },
        )
        return coverageZoneRepository.save(updated).toDto()
    }

    @Transactional
    @CacheEvict(
        cacheNames = [
            SmartMapCacheConfiguration.SMART_MAP_SUMMARY_CACHE,
            SmartMapCacheConfiguration.SMART_MAP_SUGGESTIONS_CACHE,
        ],
        allEntries = true,
    )
    fun deleteCoverageZone(id: Int, userType: String?) {
        requireIntelligenceManager(userType)
        if (!coverageZoneRepository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        coverageZoneRepository.deleteById(id)
    }

    @Transactional(readOnly = true)
    fun listSalesLeads(): List<SalesLeadMapDto> =
        salesLeadMapRepository.findAll().map { it.toDto() }

    @Transactional
    @CacheEvict(
        cacheNames = [
            SmartMapCacheConfiguration.SMART_MAP_SUMMARY_CACHE,
            SmartMapCacheConfiguration.SMART_MAP_SUGGESTIONS_CACHE,
        ],
        allEntries = true,
    )
    fun createSalesLead(request: SalesLeadMapRequest, userType: String?): SalesLeadMapDto {
        requireIntelligenceManager(userType)
        return salesLeadMapRepository.save(request.toEntity()).toDto()
    }

    @Transactional
    @CacheEvict(
        cacheNames = [
            SmartMapCacheConfiguration.SMART_MAP_SUMMARY_CACHE,
            SmartMapCacheConfiguration.SMART_MAP_SUGGESTIONS_CACHE,
        ],
        allEntries = true,
    )
    fun updateSalesLead(id: Int, request: SalesLeadMapRequest, userType: String?): SalesLeadMapDto {
        requireIntelligenceManager(userType)
        val existing = salesLeadMapRepository.findById(id).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
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
        return salesLeadMapRepository.save(updated).toDto()
    }

    @Transactional
    @CacheEvict(
        cacheNames = [
            SmartMapCacheConfiguration.SMART_MAP_SUMMARY_CACHE,
            SmartMapCacheConfiguration.SMART_MAP_SUGGESTIONS_CACHE,
        ],
        allEntries = true,
    )
    fun deleteSalesLead(id: Int, userType: String?) {
        requireIntelligenceManager(userType)
        if (!salesLeadMapRepository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        salesLeadMapRepository.deleteById(id)
    }

    @Transactional(readOnly = true)
    fun listCommercialOpportunities(): List<CommercialOpportunityDto> =
        commercialOpportunityRepository.findAll().map { it.toDto() }

    @Transactional
    @CacheEvict(
        cacheNames = [
            SmartMapCacheConfiguration.SMART_MAP_SUMMARY_CACHE,
            SmartMapCacheConfiguration.SMART_MAP_SUGGESTIONS_CACHE,
        ],
        allEntries = true,
    )
    fun createCommercialOpportunity(
        request: CommercialOpportunityRequest,
        userType: String?,
    ): CommercialOpportunityDto {
        requireIntelligenceManager(userType)
        return commercialOpportunityRepository.save(request.toEntity()).toDto()
    }

    @Transactional
    @CacheEvict(
        cacheNames = [
            SmartMapCacheConfiguration.SMART_MAP_SUMMARY_CACHE,
            SmartMapCacheConfiguration.SMART_MAP_SUGGESTIONS_CACHE,
        ],
        allEntries = true,
    )
    fun updateCommercialOpportunity(
        id: Int,
        request: CommercialOpportunityRequest,
        userType: String?,
    ): CommercialOpportunityDto {
        requireIntelligenceManager(userType)
        val existing = commercialOpportunityRepository.findById(id).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
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
        return commercialOpportunityRepository.save(updated).toDto()
    }

    @Transactional
    @CacheEvict(
        cacheNames = [
            SmartMapCacheConfiguration.SMART_MAP_SUMMARY_CACHE,
            SmartMapCacheConfiguration.SMART_MAP_SUGGESTIONS_CACHE,
        ],
        allEntries = true,
    )
    fun deleteCommercialOpportunity(id: Int, userType: String?) {
        requireIntelligenceManager(userType)
        if (!commercialOpportunityRepository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        commercialOpportunityRepository.deleteById(id)
    }

    private fun requireIntelligenceManager(userType: String?) {
        if (!SmartMapAccessPolicy.canManageIntelligence(userType)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN)
        }
    }
}
