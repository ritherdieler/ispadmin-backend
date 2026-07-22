package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import java.time.LocalDate
import java.time.LocalDateTime

data class SmartMapSummaryDto(
    val clients: List<SmartMapClientDto>,
    val zones: List<SmartMapZoneDto>,
    val kpis: SmartMapKpisDto,
    val coverageZones: List<CoverageZoneDto> = emptyList(),
    val salesLeads: List<SalesLeadMapDto> = emptyList(),
    val commercialOpportunities: List<CommercialOpportunityDto> = emptyList(),
    val alerts: List<SmartMapAlertDto> = emptyList(),
    val rankings: SmartMapRankingsDto? = null,
    val tickets: List<AssistanceTicketDto> = emptyList(),
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
    val facadePhotoUrl: String? = null,
)

data class SmartMapCollectionRouteStopDto(
    val order: Int,
    val clientId: Int,
    val fullName: String,
    val address: String?,
    val place: String?,
    val totalDebt: Double,
    val location: GeoLocationDto,
    val facadePhotoUrl: String? = null,
    val visitStatus: String? = null,
    val lastVisitAt: LocalDateTime? = null,
    val lastVisitComment: String? = null,
    val distanceFromCollectorMeters: Double? = null,
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
    val routeSegments: List<SmartMapCollectionRouteSegmentDto> = emptyList(),
    val failedSegmentCount: Int = 0,
)

data class SmartMapCollectionRouteSegmentDto(
    val fromOrder: Int,
    val toOrder: Int,
    val fromClientId: Int? = null,
    val toClientId: Int? = null,
    val fromLocation: GeoLocationDto,
    val toLocation: GeoLocationDto,
    val path: List<GeoLocationDto>,
    val distanceMeters: Double,
    val durationSeconds: Double? = null,
    val routingStatus: String,
    val fallbackReason: String? = null,
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

data class SmartMapRoadRouteAlternativeDto(
    val label: String,
    val path: List<GeoLocationDto>,
    val distanceMeters: Double,
    val durationSeconds: Double? = null,
    val routingStatus: String,
    val geometryGeoJson: String? = null,
)

data class SmartMapRoadRouteAlternativesDto(
    val origin: GeoLocationDto,
    val destination: GeoLocationDto,
    val alternatives: List<SmartMapRoadRouteAlternativeDto>,
)

data class SmartMapNavigationStepDto(
    val instruction: String,
    val maneuverType: String,
    val modifier: String? = null,
    val distanceMeters: Double,
    val durationSeconds: Double? = null,
    val location: GeoLocationDto,
    val streetName: String? = null,
)

data class SmartMapNavigationRouteDto(
    val origin: GeoLocationDto,
    val destination: GeoLocationDto,
    val path: List<GeoLocationDto>,
    val distanceMeters: Double,
    val durationSeconds: Double? = null,
    val routingStatus: String,
    val steps: List<SmartMapNavigationStepDto>,
)

data class CollectionVisitRequestDto(
    val clientId: Int,
    val status: String,
    val comment: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val routeType: String = "sector",
    val zoneName: String? = null,
    val collectorUserId: Int? = null,
)

data class CollectionVisitLogDto(
    val id: Int,
    val subscriptionId: Int?,
    val clientId: Int,
    val routeType: String,
    val zoneName: String?,
    val collectorUserId: Int?,
    val status: String,
    val comment: String?,
    val latitude: Double?,
    val longitude: Double?,
    val visitedAt: LocalDateTime,
)

data class SmartMapCollectionRouteRecalculateRequestDto(
    val place: String? = null,
    val routeType: String = "sector",
    val collectorLat: Double,
    val collectorLng: Double,
    val collectorAccuracyMeters: Double? = null,
    val remainingClientIds: List<Int>,
    val includeRoadGeometry: Boolean = false,
    val debtPeriod: String? = null,
    val debtDateFrom: LocalDate? = null,
    val debtDateTo: LocalDate? = null,
    /** Solo visitas registradas desde este instante cuentan para visitStatus. */
    val routeSessionStartedAt: LocalDateTime? = null,
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
