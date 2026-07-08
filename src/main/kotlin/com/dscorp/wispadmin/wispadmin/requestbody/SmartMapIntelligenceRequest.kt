package com.dscorp.wispadmin.wispadmin.requestbody

import com.dscorp.wispadmin.wispadmin.data.model.CommercialOpportunity
import com.dscorp.wispadmin.wispadmin.data.model.CoverageStatus
import com.dscorp.wispadmin.wispadmin.data.model.CoverageType
import com.dscorp.wispadmin.wispadmin.data.model.CoverageZone
import com.dscorp.wispadmin.wispadmin.data.model.OpportunityEvaluationStatus
import com.dscorp.wispadmin.wispadmin.data.model.OpportunityPriority
import com.dscorp.wispadmin.wispadmin.data.model.SalesLeadMap
import com.dscorp.wispadmin.wispadmin.data.model.SalesLeadStatus

data class CoverageZoneRequest(
    val name: String,
    val coverageType: CoverageType = CoverageType.PARTIAL,
    val status: CoverageStatus = CoverageStatus.ACTIVE,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val geometryGeoJson: String? = null,
    val notes: String? = null,
) {
    fun toEntity() = CoverageZone(
        name = name.trim(),
        coverageType = coverageType,
        status = status,
        latitude = latitude,
        longitude = longitude,
        geometryGeoJson = geometryGeoJson?.trim()?.takeIf { it.isNotEmpty() },
        notes = notes?.trim(),
    )
}

data class SalesLeadMapRequest(
    val referenceName: String,
    val phone: String? = null,
    val sector: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val source: String? = null,
    val leadStatus: SalesLeadStatus = SalesLeadStatus.NEW,
    val notes: String? = null,
) {
    fun toEntity() = SalesLeadMap(
        referenceName = referenceName.trim(),
        phone = phone?.trim(),
        sector = sector?.trim(),
        latitude = latitude,
        longitude = longitude,
        source = source?.trim(),
        leadStatus = leadStatus,
        notes = notes?.trim(),
    )
}

data class CommercialOpportunityRequest(
    val zoneName: String,
    val priority: OpportunityPriority = OpportunityPriority.MEDIUM,
    val reason: String? = null,
    val estimatedClients: Int = 0,
    val evaluationStatus: OpportunityEvaluationStatus = OpportunityEvaluationStatus.PENDING,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val notes: String? = null,
) {
    fun toEntity() = CommercialOpportunity(
        zoneName = zoneName.trim(),
        priority = priority,
        reason = reason?.trim(),
        estimatedClients = estimatedClients.coerceAtLeast(0),
        evaluationStatus = evaluationStatus,
        latitude = latitude,
        longitude = longitude,
        notes = notes?.trim(),
    )
}
