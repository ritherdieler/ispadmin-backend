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
    val installationType: String?,
    val locationSource: String = "place_fallback",
)

data class SmartMapCollectionRouteStopDto(
    val order: Int,
    val clientId: Int,
    val fullName: String,
    val address: String?,
    val place: String?,
    val totalDebt: Double,
    val location: GeoLocationDto,
)

data class SmartMapCollectionRouteDto(
    val zoneName: String,
    val startPoint: GeoLocationDto,
    val usedCurrentLocation: Boolean = true,
    val stops: List<SmartMapCollectionRouteStopDto>,
    val path: List<GeoLocationDto>,
    val totalDistanceMeters: Double,
    val totalDebt: Double,
    val stopCount: Int,
    val excludedCount: Int,
    val excludedReasons: Map<String, Int> = emptyMap(),
    val collectorAccuracyMeters: Double? = null,
    val sectorHasPolygon: Boolean = false,
    val roadPath: List<GeoLocationDto>? = null,
    val roadDistanceMeters: Double? = null,
    val roadDurationSeconds: Double? = null,
    val routingStatus: String? = null,
    val geometryGeoJson: String? = null,
    val routeType: String = "sector",
    val sectorsIncluded: List<String> = emptyList(),
)

data class SmartMapCollectionPendingClientDto(
    val clientId: Int,
    val fullName: String,
    val place: String?,
    val totalDebt: Double,
    val markedAt: String,
)

data class SmartMapCollectionPendingSummaryDto(
    val clients: List<SmartMapCollectionPendingClientDto>,
    val clientCount: Int,
    val sectorCount: Int,
    val totalDebt: Double,
    val sectors: List<String>,
    val debtPeriod: String = "LAST_1_MONTH",
    val debtDateFrom: String? = null,
    val debtDateTo: String? = null,
    val periodLabel: String = "",
)

data class SmartMapRoadRouteDto(
    val sectorName: String,
    val origin: GeoLocationDto,
    val destination: GeoLocationDto,
    val path: List<GeoLocationDto>,
    val distanceMeters: Double,
    val durationSeconds: Double? = null,
    val routingStatus: String,
    val geometryGeoJson: String? = null,
)

data class SmartMapValidationErrorDto(
    val code: String,
    val message: String,
    val sectorName: String,
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
