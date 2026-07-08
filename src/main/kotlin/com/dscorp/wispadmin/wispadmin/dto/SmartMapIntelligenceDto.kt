package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.CommercialOpportunity
import com.dscorp.wispadmin.wispadmin.data.model.CoverageZone
import com.dscorp.wispadmin.wispadmin.data.model.SalesLeadMap
import java.util.Date

data class CoverageZoneDto(
    val id: Int,
    val name: String,
    val coverageType: String,
    val status: String,
    val latitude: Double?,
    val longitude: Double?,
    val geometryGeoJson: String? = null,
    val notes: String?,
    val createdAt: Date,
)

data class SalesLeadMapDto(
    val id: Int,
    val referenceName: String,
    val phone: String?,
    val sector: String?,
    val latitude: Double?,
    val longitude: Double?,
    val source: String?,
    val leadStatus: String,
    val notes: String?,
    val createdAt: Date,
)

data class CommercialOpportunityDto(
    val id: Int,
    val zoneName: String,
    val priority: String,
    val reason: String?,
    val estimatedClients: Int,
    val evaluationStatus: String,
    val latitude: Double?,
    val longitude: Double?,
    val notes: String?,
    val createdAt: Date,
)

data class SmartMapAlertDto(
    val id: String,
    val severity: String,
    val title: String,
    val message: String,
    val zoneName: String?,
)

data class SmartMapRankingItemDto(
    val zoneName: String,
    val value: Double,
    val label: String,
)

data class SmartMapRankingsDto(
    val topDebt: List<SmartMapRankingItemDto>,
    val topIncidence: List<SmartMapRankingItemDto>,
    val topGrowth: List<SmartMapRankingItemDto>,
    val topOpportunity: List<SmartMapRankingItemDto>,
    val topCancellationRisk: List<SmartMapRankingItemDto> = emptyList(),
)

fun CoverageZone.toDto() = CoverageZoneDto(
    id = id,
    name = name,
    coverageType = coverageType.name,
    status = status.name,
    latitude = latitude,
    longitude = longitude,
    geometryGeoJson = geometryGeoJson,
    notes = notes,
    createdAt = createdAt,
)

fun SalesLeadMap.toDto() = SalesLeadMapDto(
    id = id,
    referenceName = referenceName,
    phone = phone,
    sector = sector,
    latitude = latitude,
    longitude = longitude,
    source = source,
    leadStatus = leadStatus.name,
    notes = notes,
    createdAt = createdAt,
)

fun CommercialOpportunity.toDto() = CommercialOpportunityDto(
    id = id,
    zoneName = zoneName,
    priority = priority.name,
    reason = reason,
    estimatedClients = estimatedClients,
    evaluationStatus = evaluationStatus.name,
    latitude = latitude,
    longitude = longitude,
    notes = notes,
    createdAt = createdAt,
)
