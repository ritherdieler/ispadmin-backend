package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.dto.CollectionVisitLogDto
import com.dscorp.wispadmin.wispadmin.dto.CollectionVisitRequestDto
import com.dscorp.wispadmin.wispadmin.dto.GeoLocationDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapCollectionRouteDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapCollectionRouteRecalculateRequestDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapCollectionPendingSummaryDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapCollectionPlaceDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapCoverageCheckDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapNavigationRouteDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapRoadRouteAlternativesDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapRoadRouteDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapSuggestionDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapSummaryDto
import com.dscorp.wispadmin.wispadmin.service.CollectionVisitService
import com.dscorp.wispadmin.wispadmin.service.SmartMapRoadRouteService
import com.dscorp.wispadmin.wispadmin.service.SmartMapService
import com.dscorp.wispadmin.wispadmin.smartmap.SmartMapAccessPolicy
import kotlinx.coroutines.runBlocking
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.LocalDateTime
import javax.validation.Valid

@RestController
@RequestMapping("/smart-map")
class SmartMapController(
    private val smartMapService: SmartMapService,
    private val smartMapRoadRouteService: SmartMapRoadRouteService,
    private val collectionVisitService: CollectionVisitService,
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
        @RequestParam(required = false) userType: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateFrom: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateTo: LocalDate?,
        @RequestParam(required = false, defaultValue = "subscriptions") dateScope: String,
        @RequestParam(required = false, defaultValue = "full") view: String,
    ): ResponseEntity<SmartMapSummaryDto> {
        return ResponseEntity.ok(
            smartMapService.getSummary(
                includeDebt = includeDebt && SmartMapAccessPolicy.canViewDebt(userType),
                includeTickets = includeTickets && SmartMapAccessPolicy.canViewTickets(userType),
                search = search,
                serviceStatuses = serviceStatuses,
                installationType = installationType,
                place = place,
                plan = plan,
                onlyWithDebt = onlyWithDebt,
                dateFrom = dateFrom,
                dateTo = dateTo,
                dateScope = dateScope,
                view = view,
            ),
        )
    }

    @GetMapping("/suggestions")
    fun getSuggestions(): ResponseEntity<List<SmartMapSuggestionDto>> =
        ResponseEntity.ok(smartMapService.getSuggestions())

    @GetMapping("/road-route")
    fun getRoadRoute(
        @RequestParam sector: String,
        @RequestParam originLatitude: Double,
        @RequestParam originLongitude: Double,
        @RequestParam destinationLatitude: Double,
        @RequestParam destinationLongitude: Double,
    ): ResponseEntity<SmartMapRoadRouteDto> = runBlocking {
        ResponseEntity.ok(
            smartMapRoadRouteService.buildRoadRoute(
                sector = sector,
                origin = GeoLocationDto(originLatitude, originLongitude),
                destination = GeoLocationDto(destinationLatitude, destinationLongitude),
            ),
        )
    }

    @GetMapping("/road-route/alternatives")
    fun getRoadRouteAlternatives(
        @RequestParam originLat: Double,
        @RequestParam originLng: Double,
        @RequestParam destLat: Double,
        @RequestParam destLng: Double,
        @RequestParam(required = false, defaultValue = "3") count: Int,
    ): ResponseEntity<SmartMapRoadRouteAlternativesDto> = runBlocking {
        ResponseEntity.ok(
            smartMapRoadRouteService.buildRoadRouteAlternatives(
                origin = GeoLocationDto(originLat, originLng),
                destination = GeoLocationDto(destLat, destLng),
                count = count,
            ),
        )
    }

    @GetMapping("/road-route/navigation")
    fun getRoadRouteNavigation(
        @RequestParam originLat: Double,
        @RequestParam originLng: Double,
        @RequestParam destLat: Double,
        @RequestParam destLng: Double,
        @RequestParam(required = false) destinationName: String?,
        @RequestParam(required = false) avoidManeuverRadius: Int?,
        @RequestParam(required = false, defaultValue = "false") inMotion: Boolean,
    ): ResponseEntity<SmartMapNavigationRouteDto> = runBlocking {
        ResponseEntity.ok(
            smartMapRoadRouteService.buildNavigationRoute(
                origin = GeoLocationDto(originLat, originLng),
                destination = GeoLocationDto(destLat, destLng),
                destinationName = destinationName,
                avoidManeuverRadius = avoidManeuverRadius,
                inMotion = inMotion,
            ),
        )
    }

    @PostMapping("/collection-route/recalculate")
    fun recalculateCollectionRoute(
        @Valid @RequestBody request: SmartMapCollectionRouteRecalculateRequestDto,
        @RequestParam(required = false) userType: String?,
    ): ResponseEntity<SmartMapCollectionRouteDto> = runBlocking {
        requireDebtAccess(userType)

        val route = smartMapService.recalculateCollectionRoute(
            place = request.place,
            routeType = request.routeType,
            collectorLatitude = request.collectorLat,
            collectorLongitude = request.collectorLng,
            collectorAccuracyMeters = request.collectorAccuracyMeters,
            remainingClientIds = request.remainingClientIds,
            debtPeriod = request.debtPeriod,
            debtDateFrom = request.debtDateFrom,
            debtDateTo = request.debtDateTo,
            visitSince = request.routeSessionStartedAt,
            selectionPolygonGeoJson = request.selectionPolygonGeoJson,
        )

        val enrichedRoute = if (request.includeRoadGeometry && route.stopCount > 0) {
            smartMapRoadRouteService.enrichCollectionRoute(route)
        } else {
            route
        }

        ResponseEntity.ok(enrichedRoute)
    }

    @PostMapping("/collection-visit")
    fun registerCollectionVisit(
        @Valid @RequestBody request: CollectionVisitRequestDto,
        @RequestParam(required = false) userType: String?,
    ): ResponseEntity<CollectionVisitLogDto> {
        requireDebtAccess(userType)
        return ResponseEntity.ok(collectionVisitService.registerVisit(request))
    }

    @GetMapping("/collection-visit/recent")
    fun getRecentCollectionVisits(
        @RequestParam clientId: Int,
        @RequestParam(required = false) userType: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) since: LocalDateTime?,
    ): ResponseEntity<List<CollectionVisitLogDto>> {
        requireDebtAccess(userType)
        val effectiveSince = since ?: collectionVisitService.defaultCommentLookbackSince()
        return ResponseEntity.ok(collectionVisitService.getRecentVisits(clientId, effectiveSince))
    }

    @GetMapping("/collection-route")
    fun getCollectionRoute(
        @RequestParam place: String,
        @RequestParam collectorLatitude: Double,
        @RequestParam collectorLongitude: Double,
        @RequestParam(required = false) collectorAccuracyMeters: Double?,
        @RequestParam(required = false) userType: String?,
        @RequestParam(required = false, defaultValue = "false") includeRoadGeometry: Boolean,
        @RequestParam(required = false) selectedClientIds: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) routeSessionStartedAt: LocalDateTime?,
    ): ResponseEntity<SmartMapCollectionRouteDto> = runBlocking {
        requireDebtAccess(userType)

        val route = smartMapService.buildCollectionRoute(
            place = place,
            collectorLatitude = collectorLatitude,
            collectorLongitude = collectorLongitude,
            collectorAccuracyMeters = collectorAccuracyMeters,
            selectedClientIds = parseSelectedClientIds(selectedClientIds),
            visitSince = routeSessionStartedAt,
        )

        val enrichedRoute = if (includeRoadGeometry && route.stopCount > 0) {
            smartMapRoadRouteService.enrichCollectionRoute(route)
        } else {
            route
        }

        ResponseEntity.ok(enrichedRoute)
    }

    @GetMapping("/collection-sweep-route")
    fun getCollectionSweepRoute(
        @RequestParam collectorLatitude: Double,
        @RequestParam collectorLongitude: Double,
        @RequestParam(required = false) collectorAccuracyMeters: Double?,
        @RequestParam(required = false, defaultValue = "LAST_1_MONTH") debtPeriod: String,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) debtDateFrom: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) debtDateTo: LocalDate?,
        @RequestParam(required = false) place: String?,
        @RequestParam(required = false) selectionPolygonGeoJson: String?,
        @RequestParam(required = false) userType: String?,
        @RequestParam(required = false, defaultValue = "false") includeRoadGeometry: Boolean,
        @RequestParam(required = false) selectedClientIds: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) routeSessionStartedAt: LocalDateTime?,
    ): ResponseEntity<SmartMapCollectionRouteDto> = runBlocking {
        requireDebtAccess(userType)

        val route = smartMapService.buildCollectionSweepRoute(
            collectorLatitude = collectorLatitude,
            collectorLongitude = collectorLongitude,
            collectorAccuracyMeters = collectorAccuracyMeters,
            debtPeriod = debtPeriod,
            debtDateFrom = debtDateFrom,
            debtDateTo = debtDateTo,
            selectedClientIds = parseSelectedClientIds(selectedClientIds),
            visitSince = routeSessionStartedAt,
            place = place,
            selectionPolygonGeoJson = selectionPolygonGeoJson,
        )

        val enrichedRoute = if (includeRoadGeometry && route.stopCount > 0) {
            smartMapRoadRouteService.enrichCollectionRoute(route)
        } else {
            route
        }

        ResponseEntity.ok(enrichedRoute)
    }

    @GetMapping("/collection-pending")
    fun getCollectionPending(
        @RequestParam(required = false, defaultValue = "LAST_1_MONTH") debtPeriod: String,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) debtDateFrom: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) debtDateTo: LocalDate?,
        @RequestParam(required = false) place: String?,
        @RequestParam(required = false) selectionPolygonGeoJson: String?,
        @RequestParam(required = false) userType: String?,
    ): ResponseEntity<SmartMapCollectionPendingSummaryDto> {
        requireDebtAccess(userType)
        return ResponseEntity.ok(
            smartMapService.getCollectionSweepPreview(
                debtPeriod = debtPeriod,
                debtDateFrom = debtDateFrom,
                debtDateTo = debtDateTo,
                place = place,
                selectionPolygonGeoJson = selectionPolygonGeoJson,
            ),
        )
    }

    @GetMapping("/collection-places")
    fun listCollectionPlaces(
        @RequestParam(required = false) userType: String?,
    ): ResponseEntity<List<SmartMapCollectionPlaceDto>> {
        requireDebtAccess(userType)
        return ResponseEntity.ok(smartMapService.listCollectionPlaces())
    }

    @GetMapping("/coverage-check")
    fun checkCoverage(
        @RequestParam latitude: Double,
        @RequestParam longitude: Double,
        @RequestParam(required = false) assignedPlace: String?,
    ): ResponseEntity<SmartMapCoverageCheckDto> =
        ResponseEntity.ok(
            smartMapService.checkCoverageAtLocation(
                latitude = latitude,
                longitude = longitude,
                assignedPlace = assignedPlace,
            ),
        )

    private fun requireDebtAccess(userType: String?) {
        if (!SmartMapAccessPolicy.canViewDebt(userType)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN)
        }
    }

    companion object {
        private fun parseSelectedClientIds(rawIds: String?): Set<Int>? {
            val ids = rawIds
                ?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.filter { it > 0 }
                ?.toSet()
                ?: return null

            return ids.ifEmpty { null }
        }
    }
}
