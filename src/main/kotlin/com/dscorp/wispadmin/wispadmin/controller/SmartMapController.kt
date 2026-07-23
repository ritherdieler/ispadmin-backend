package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.Modules
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.dto.GeoLocationDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapCollectionRouteDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapCoverageCheckDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapCollectionPendingSummaryDto
import com.dscorp.wispadmin.wispadmin.dto.CollectionVisitLogDto
import com.dscorp.wispadmin.wispadmin.dto.CollectionVisitRequestDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapCollectionRouteRecalculateRequestDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapNavigationRouteDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapRoadRouteAlternativesDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapRoadRouteDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapSuggestionDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapSummaryDto
import com.dscorp.wispadmin.wispadmin.extensions.toErrorLog
import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import com.dscorp.wispadmin.wispadmin.service.CollectionVisitService
import com.dscorp.wispadmin.wispadmin.service.SectorValidationService
import com.dscorp.wispadmin.wispadmin.service.SmartMapRoadRouteService
import com.dscorp.wispadmin.wispadmin.service.SmartMapService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking

@RestController
@RequestMapping("/smart-map")
class SmartMapController(
    private val smartMapService: SmartMapService,
    private val smartMapRoadRouteService: SmartMapRoadRouteService,
    private val collectionVisitService: CollectionVisitService,
    private val sectorValidationService: SectorValidationService,
    private val errorLogRepository: ErrorLogRepository,
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
        return try {
            val normalizedType = userType?.trim()?.uppercase()
            val effectiveIncludeDebt = includeDebt && normalizedType !in DEBT_RESTRICTED_ROLES
            val effectiveIncludeTickets = includeTickets && normalizedType !in TICKET_RESTRICTED_ROLES
            ResponseEntity.ok(
                smartMapService.getSummary(
                    includeDebt = effectiveIncludeDebt,
                    includeTickets = effectiveIncludeTickets,
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
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            e.printStackTrace()
            ResponseEntity.status(500).body(null)
        }
    }

    @GetMapping("/suggestions")
    fun getSuggestions(): ResponseEntity<List<SmartMapSuggestionDto>> {
        return try {
            ResponseEntity.ok(smartMapService.getSuggestions())
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            ResponseEntity.status(500).body(emptyList())
        }
    }

    @GetMapping("/road-route")
    fun getRoadRoute(
        @RequestParam sector: String,
        @RequestParam originLatitude: Double,
        @RequestParam originLongitude: Double,
        @RequestParam destinationLatitude: Double,
        @RequestParam destinationLongitude: Double,
    ): ResponseEntity<SmartMapRoadRouteDto> {
        return ResponseEntity.ok(
            runBlocking {
                smartMapRoadRouteService.buildRoadRoute(
                    sector = sector,
                    origin = GeoLocationDto(originLatitude, originLongitude),
                    destination = GeoLocationDto(destinationLatitude, destinationLongitude),
                )
            },
        )
    }

    @GetMapping("/road-route/alternatives")
    fun getRoadRouteAlternatives(
        @RequestParam originLat: Double,
        @RequestParam originLng: Double,
        @RequestParam destLat: Double,
        @RequestParam destLng: Double,
        @RequestParam(required = false, defaultValue = "3") count: Int,
    ): ResponseEntity<SmartMapRoadRouteAlternativesDto> {
        return ResponseEntity.ok(
            runBlocking {
                smartMapRoadRouteService.buildRoadRouteAlternatives(
                    origin = GeoLocationDto(originLat, originLng),
                    destination = GeoLocationDto(destLat, destLng),
                    count = count,
                )
            },
        )
    }

    @GetMapping("/road-route/navigation")
    fun getRoadRouteNavigation(
        @RequestParam originLat: Double,
        @RequestParam originLng: Double,
        @RequestParam destLat: Double,
        @RequestParam destLng: Double,
    ): ResponseEntity<SmartMapNavigationRouteDto> {
        return ResponseEntity.ok(
            runBlocking {
                smartMapRoadRouteService.buildNavigationRoute(
                    origin = GeoLocationDto(originLat, originLng),
                    destination = GeoLocationDto(destLat, destLng),
                )
            },
        )
    }

    @PostMapping("/collection-route/recalculate")
    fun recalculateCollectionRoute(
        @RequestBody request: SmartMapCollectionRouteRecalculateRequestDto,
        @RequestParam(required = false) userType: String?,
    ): ResponseEntity<SmartMapCollectionRouteDto> {
        return try {
            val normalizedType = userType?.trim()?.uppercase()
            if (normalizedType in DEBT_RESTRICTED_ROLES) {
                return ResponseEntity.status(403).body(null)
            }

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
            )

            val enrichedRoute = if (request.includeRoadGeometry && route.stopCount > 0) {
                runBlocking { smartMapRoadRouteService.enrichCollectionRoute(route) }
            } else {
                route
            }

            ResponseEntity.ok(enrichedRoute)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(400).body(null)
        } catch (e: Exception) {
            if (e is com.dscorp.wispadmin.wispadmin.exception.SmartMapSectorValidationException) {
                throw e
            }
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            e.printStackTrace()
            ResponseEntity.status(500).body(null)
        }
    }

    @PostMapping("/collection-visit")
    fun registerCollectionVisit(
        @RequestBody request: CollectionVisitRequestDto,
        @RequestParam(required = false) userType: String?,
    ): ResponseEntity<CollectionVisitLogDto> {
        return try {
            val normalizedType = userType?.trim()?.uppercase()
            if (normalizedType in DEBT_RESTRICTED_ROLES) {
                return ResponseEntity.status(403).body(null)
            }

            ResponseEntity.ok(collectionVisitService.registerVisit(request))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(400).body(null)
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            e.printStackTrace()
            ResponseEntity.status(500).body(null)
        }
    }

    @GetMapping("/collection-visit/recent")
    fun getRecentCollectionVisits(
        @RequestParam clientId: Int,
        @RequestParam(required = false) userType: String?,
    ): ResponseEntity<List<CollectionVisitLogDto>> {
        return try {
            val normalizedType = userType?.trim()?.uppercase()
            if (normalizedType in DEBT_RESTRICTED_ROLES) {
                return ResponseEntity.status(403).body(emptyList())
            }

            ResponseEntity.ok(collectionVisitService.getRecentVisits(clientId))
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            e.printStackTrace()
            ResponseEntity.status(500).body(emptyList())
        }
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
    ): ResponseEntity<SmartMapCollectionRouteDto> {
        return try {
            val normalizedType = userType?.trim()?.uppercase()
            if (normalizedType in DEBT_RESTRICTED_ROLES) {
                return ResponseEntity.status(403).body(null)
            }

            val route = smartMapService.buildCollectionRoute(
                place = place,
                collectorLatitude = collectorLatitude,
                collectorLongitude = collectorLongitude,
                collectorAccuracyMeters = collectorAccuracyMeters,
                selectedClientIds = parseSelectedClientIds(selectedClientIds),
                visitSince = routeSessionStartedAt,
            )

            val enrichedRoute = if (includeRoadGeometry && route.stopCount > 0) {
                runBlocking { smartMapRoadRouteService.enrichCollectionRoute(route) }
            } else {
                route
            }

            ResponseEntity.ok(enrichedRoute)
        } catch (e: Exception) {
            if (e is com.dscorp.wispadmin.wispadmin.exception.SmartMapSectorValidationException) {
                throw e
            }
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            e.printStackTrace()
            ResponseEntity.status(500).body(null)
        }
    }

    @GetMapping("/collection-sweep-route")
    fun getCollectionSweepRoute(
        @RequestParam collectorLatitude: Double,
        @RequestParam collectorLongitude: Double,
        @RequestParam(required = false) collectorAccuracyMeters: Double?,
        @RequestParam(required = false, defaultValue = "LAST_1_MONTH") debtPeriod: String,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) debtDateFrom: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) debtDateTo: LocalDate?,
        @RequestParam(required = false) userType: String?,
        @RequestParam(required = false, defaultValue = "false") includeRoadGeometry: Boolean,
        @RequestParam(required = false) selectedClientIds: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) routeSessionStartedAt: LocalDateTime?,
    ): ResponseEntity<SmartMapCollectionRouteDto> {
        return try {
            val normalizedType = userType?.trim()?.uppercase()
            if (normalizedType in DEBT_RESTRICTED_ROLES) {
                return ResponseEntity.status(403).body(null)
            }

            val route = smartMapService.buildCollectionSweepRoute(
                collectorLatitude = collectorLatitude,
                collectorLongitude = collectorLongitude,
                collectorAccuracyMeters = collectorAccuracyMeters,
                debtPeriod = debtPeriod,
                debtDateFrom = debtDateFrom,
                debtDateTo = debtDateTo,
                selectedClientIds = parseSelectedClientIds(selectedClientIds),
                visitSince = routeSessionStartedAt,
            )

            val enrichedRoute = if (includeRoadGeometry && route.stopCount > 0) {
                runBlocking { smartMapRoadRouteService.enrichCollectionRoute(route) }
            } else {
                route
            }

            ResponseEntity.ok(enrichedRoute)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(400).body(null)
        } catch (e: Exception) {
            if (e is com.dscorp.wispadmin.wispadmin.exception.SmartMapSectorValidationException) {
                throw e
            }
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            e.printStackTrace()
            ResponseEntity.status(500).body(null)
        }
    }

    @GetMapping("/collection-pending")
    fun getCollectionPending(
        @RequestParam(required = false, defaultValue = "LAST_1_MONTH") debtPeriod: String,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) debtDateFrom: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) debtDateTo: LocalDate?,
        @RequestParam(required = false) userType: String?,
    ): ResponseEntity<SmartMapCollectionPendingSummaryDto> {
        return try {
            val normalizedType = userType?.trim()?.uppercase()
            if (normalizedType in DEBT_RESTRICTED_ROLES) {
                return ResponseEntity.status(403).body(null)
            }

            ResponseEntity.ok(
                smartMapService.getCollectionSweepPreview(
                    debtPeriod = debtPeriod,
                    debtDateFrom = debtDateFrom,
                    debtDateTo = debtDateTo,
                ),
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(400).body(null)
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            e.printStackTrace()
            ResponseEntity.status(500).body(null)
        }
    }

    @GetMapping("/coverage-check")
    fun checkCoverage(
        @RequestParam latitude: Double,
        @RequestParam longitude: Double,
        @RequestParam(required = false) assignedPlace: String?,
    ): ResponseEntity<SmartMapCoverageCheckDto> {
        return try {
            ResponseEntity.ok(
                smartMapService.checkCoverageAtLocation(
                    latitude = latitude,
                    longitude = longitude,
                    assignedPlace = assignedPlace,
                ),
            )
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            e.printStackTrace()
            ResponseEntity.status(500).body(null)
        }
    }

    companion object {
        private val DEBT_RESTRICTED_ROLES = setOf("TECHNICIAN", "SALES")
        private val TICKET_RESTRICTED_ROLES = setOf("ACCOUNTANT", "SALES")

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
