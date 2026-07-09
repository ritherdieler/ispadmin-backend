package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import java.time.LocalDate

data class SmartMapSummaryDto(
    val clients: List<SmartMapClientDto>,
    val zones: List<SmartMapZoneDto>,
    val kpis: SmartMapKpisDto,
    val coverageZones: List<CoverageZoneDto> = emptyList(),
    val salesLeads: List<SalesLeadMapDto> = emptyList(),
    val commercialOpportunities: List<CommercialOpportunityDto> = emptyList(),
    val alerts: List<SmartMapAlertDto> = emptyList(),
    val rankings: SmartMapRankingsDto? = null,
)

data class SmartMapClientDto(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val plan: String?,
    val location: GeoLocationDto,
    val serviceStatus: ServiceStatus,
    val address: String?,
    val phone: String?,
    val dni: String?,
    val ip: String?,
    val subscriptionDate: Long?,
    val lastCutOffDate: LocalDate?,
    val pendingInvoiceQuantity: Int,
    val totalDebt: Double,
    val place: String?,
    val installationType: String?
)

data class SmartMapZoneDto(
    val zoneName: String,
    val clientCount: Int,
    val activeClients: Int,
    val totalDebt: Double,
    val openTickets: Int,
    val riskLevel: String,
    val debtLevel: String,
    val ticketLevel: String,
    val latitude: Double?,
    val longitude: Double?,
    val clientDensity: Int = 0,
    val revenue: Double = 0.0,
    val incidenceRate: Double = 0.0,
    val growthRate: Double = 0.0,
    val opportunityScore: Double = 0.0,
    val displayName: String = "",
    val hasGeolocatedClients: Boolean = false,
    val coordinateSource: String = "none",
    val cancellations: Int = 0,
    val cancellationRisk: Double = 0.0,
    val areaGeoJson: String? = null,
)

data class SmartMapKpisDto(
    val totalClients: Int,
    val activeClients: Int,
    val geolocatedClients: Int,
    val totalDebt: Double,
    val totalZones: Int,
    val openTickets: Int,
    val unlocatedClients: Int = 0
)

data class SmartMapNearestNapBoxDto(
    val id: Int,
    val code: String,
    val placeName: String?,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Int,
    val availablePorts: Int,
    val totalPorts: Int,
)

data class SmartMapCoverageCheckDto(
    val hasCoverage: Boolean,
    val detectedPlaceName: String?,
    val assignedPlaceName: String?,
    val matchesAssignedPlace: Boolean?,
    val message: String,
    val nearestNapBox: SmartMapNearestNapBoxDto? = null,
    val nearCoverage: Boolean = false,
    val nearCoverageThresholdMeters: Int = 500,
)
