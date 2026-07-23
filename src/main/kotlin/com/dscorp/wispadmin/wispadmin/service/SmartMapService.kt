package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicket
import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicketStatus
import com.dscorp.wispadmin.wispadmin.data.model.Payment
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.dto.AssistanceTicketDto
import com.dscorp.wispadmin.wispadmin.dto.GeoLocationDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapClientDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapKpisDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapSummaryDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapZoneDto
import com.dscorp.wispadmin.wispadmin.dto.CoverageZoneDto
import com.dscorp.wispadmin.wispadmin.dto.CommercialOpportunityDto
import com.dscorp.wispadmin.wispadmin.dto.GeographicPerformanceResumeDto
import com.dscorp.wispadmin.wispadmin.dto.SalesLeadMapDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapAlertDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapRankingItemDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapRankingsDto
import com.dscorp.wispadmin.wispadmin.data.model.NapBox
import com.dscorp.wispadmin.wispadmin.dto.SmartMapCollectionRouteDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapCollectionRouteStopDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapCollectionPendingClientDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapCollectionPendingSummaryDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapCoverageCheckDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapNearestNapBoxDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapSuggestionDto
import com.dscorp.wispadmin.wispadmin.dto.toDto
import com.dscorp.wispadmin.wispadmin.repository.AssistanceTicketRepository
import com.dscorp.wispadmin.wispadmin.repository.CommercialOpportunityRepository
import com.dscorp.wispadmin.wispadmin.repository.CoverageZoneRepository
import com.dscorp.wispadmin.wispadmin.repository.NapBoxRepository
import com.dscorp.wispadmin.wispadmin.repository.PlaceRepository
import com.dscorp.wispadmin.wispadmin.repository.SalesLeadMapRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.config.SmartMapCacheConfiguration
import com.dscorp.wispadmin.wispadmin.util.PlaceGeometryUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class SmartMapService(
    private val subscriptionRepository: SubscriptionRepository,
    private val assistanceTicketRepository: AssistanceTicketRepository,
    private val placeRepository: PlaceRepository,
    private val dashBoardService: DashBoardService,
    private val coverageZoneRepository: CoverageZoneRepository,
    private val salesLeadMapRepository: SalesLeadMapRepository,
    private val commercialOpportunityRepository: CommercialOpportunityRepository,
    private val napBoxRepository: NapBoxRepository,
    private val borneManagementService: BorneManagementService,
    private val collectionVisitService: CollectionVisitService,
    @Value("\${smartmap.nearCoverageThresholdMeters:500}")
    private val nearCoverageThresholdMeters: Int,
) {

    @Transactional(readOnly = true)
    @Cacheable(
        cacheNames = [SmartMapCacheConfiguration.SMART_MAP_SUMMARY_CACHE],
        key = "{#includeDebt,#includeTickets,#search,#serviceStatuses,#installationType,#place,#plan,#onlyWithDebt,#dateFrom,#dateTo,#dateScope,#view}"
    )
    fun getSummary(
        includeDebt: Boolean = true,
        includeTickets: Boolean = true,
        search: String? = null,
        serviceStatuses: List<ServiceStatus>? = null,
        installationType: String? = null,
        place: String? = null,
        plan: String? = null,
        onlyWithDebt: Boolean = false,
        dateFrom: LocalDate? = null,
        dateTo: LocalDate? = null,
        dateScope: String = "subscriptions",
        view: String = "full",
    ): SmartMapSummaryDto {
        val isLightView = view.equals("light", ignoreCase = true)
        val filterSubscriptionsByDate = dateScope != "tickets"
        val filterTicketsByDate = dateScope == "tickets"
        val hasClientFilters = hasClientFilters(
            search = search,
            serviceStatuses = serviceStatuses,
            installationType = installationType,
            place = place,
            plan = plan,
            onlyWithDebt = onlyWithDebt
        )

        val filteredSubscriptions = subscriptionRepository.findAllWithRelationsForSmartMap()
            .filter {
                it.matchesFilters(
                    search = search,
                    serviceStatuses = serviceStatuses,
                    installationType = installationType,
                    place = place,
                    plan = plan,
                    onlyWithDebt = onlyWithDebt
                )
            }
            .filter { !filterSubscriptionsByDate || it.matchesSubscriptionDate(dateFrom, dateTo) }

        val clients = filteredSubscriptions.mapNotNull { it.toSmartMapClient(includeDebt) }
        val unlocatedClients = filteredSubscriptions.count { it.place != null && it.getBestLocation() == null }

        val openTicketEntities = if (includeTickets) {
            assistanceTicketRepository.findByStatusIn(
                listOf(AssistanceTicketStatus.PENDING, AssistanceTicketStatus.ASSIGNED)
            )
        } else {
            emptyList()
        }

        val visibleTicketZones = if (includeTickets && hasClientFilters) {
            place?.takeIf { it.isNotBlank() }?.let { setOf(normalizeZoneKey(it)) }
                ?: clients.map { normalizeZoneKey(it.place ?: DEFAULT_ZONE_NAME) }.toSet()
        } else {
            emptySet()
        }

        val filteredOpenTickets = if (includeTickets) {
            openTicketEntities
                .filter { ticket -> !hasClientFilters || visibleTicketZones.contains(normalizeZoneKey(ticket.getZoneName())) }
                .filter { ticket -> !filterTicketsByDate || ticket.matchesCreatedDate(dateFrom, dateTo) }
        } else {
            emptyList()
        }

        val ticketsByZone = if (includeTickets) {
            filteredOpenTickets
                .groupingBy { normalizeZoneKey(it.getZoneName()) }
                .eachCount()
        } else {
            emptyMap()
        }

        val ticketDtos = filteredOpenTickets.map { it.toSmartMapTicketDto() }

        val placeCoordinates = loadPlaceCoordinates()
        val placeAreas = loadPlaceAreas()
        val zoneDisplayNames = loadZoneDisplayNames()
        val geographicPerformance = if (isLightView) {
            GeographicPerformanceResumeDto(
                clientDensityByPlace = emptyMap(),
                revenueByPlace = emptyMap(),
                incidenceRateByPlace = emptyMap(),
                growthRateByPlace = emptyMap(),
            )
        } else {
            dashBoardService.getGeographicPerformanceData()
        }
        val cancellationsByZone = if (isLightView) {
            emptyMap()
        } else {
            dashBoardService.getCancellationsByZone()
        }
        val zones = buildZones(
            clients,
            ticketsByZone,
            includeDebt,
            includeTickets,
            placeCoordinates,
            placeAreas,
            zoneDisplayNames,
            geographicPerformance,
            cancellationsByZone,
        )
        val openTickets = if (includeTickets) zones.sumOf { it.openTickets } else 0
        val totalDebt = if (includeDebt) clients.sumOf { it.totalDebt } else 0.0

        val coverageZones = coverageZoneRepository.findAll().map { it.toDto() }
        val salesLeads = if (isLightView) emptyList() else salesLeadMapRepository.findAll().map { it.toDto() }
        val commercialOpportunities = if (isLightView) emptyList() else commercialOpportunityRepository.findAll().map { it.toDto() }
        val alerts = if (isLightView) emptyList() else buildAlerts(zones, coverageZones)
        val rankings = if (isLightView) null else buildRankings(zones)

        return SmartMapSummaryDto(
            clients = clients,
            zones = zones,
            kpis = SmartMapKpisDto(
                totalClients = clients.size,
                activeClients = clients.count { it.serviceStatus == ServiceStatus.ACTIVE },
                geolocatedClients = clients.size,
                totalDebt = totalDebt,
                totalZones = zones.size,
                openTickets = openTickets,
                unlocatedClients = unlocatedClients
            ),
            coverageZones = coverageZones,
            salesLeads = salesLeads,
            commercialOpportunities = commercialOpportunities,
            alerts = alerts,
            rankings = rankings,
            tickets = ticketDtos,
        )
    }

    // Sugerencias automaticas de sectores con potencial comercial (score >= 50).
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = [SmartMapCacheConfiguration.SMART_MAP_SUGGESTIONS_CACHE])
    fun getSuggestions(): List<SmartMapSuggestionDto> {
        val summary = getSummary()
        val coverageByKey = summary.coverageZones.associateBy { normalizeZoneKey(it.name) }

        return summary.zones.mapNotNull { zone ->
            val coverage = coverageByKey[normalizeZoneKey(zone.zoneName)]
            val reasons = mutableListOf<String>()
            var score = 0.0

            if (zone.clientDensity >= HIGH_DENSITY_THRESHOLD) {
                score += 25.0
                reasons.add("Alta densidad de clientes (${zone.clientDensity})")
            }
            if (zone.growthRate > 10.0) {
                score += 30.0
                reasons.add("Crecimiento sostenido (${zone.growthRate.toInt()}%)")
            }
            if (coverage == null) {
                score += 25.0
                reasons.add("Sin cobertura registrada")
            } else if (coverage.coverageType == "NONE") {
                score += 25.0
                reasons.add("Cobertura marcada como nula")
            }
            if (zone.incidenceRate < 10.0) {
                score += 20.0
                reasons.add("Baja incidencia de tickets (${zone.incidenceRate.toInt()}%)")
            }

            if (score < 50.0 || zone.latitude == null || zone.longitude == null) {
                return@mapNotNull null
            }

            SmartMapSuggestionDto(
                zoneName = zone.zoneName,
                score = score.coerceAtMost(100.0),
                reasons = reasons,
                estimatedClients = zone.clientCount,
                growthRate = zone.growthRate,
                incidenceRate = zone.incidenceRate,
                latitude = zone.latitude,
                longitude = zone.longitude,
            )
        }.sortedByDescending { it.score }
    }

    @Transactional(readOnly = true)
    fun checkCoverageAtLocation(
        latitude: Double,
        longitude: Double,
        assignedPlace: String?,
    ): SmartMapCoverageCheckDto {
        if (!isValidCoverageCheckCoordinate(latitude, longitude)) {
            return SmartMapCoverageCheckDto(
                hasCoverage = false,
                detectedPlaceName = null,
                assignedPlaceName = assignedPlace?.trim()?.takeIf { it.isNotEmpty() },
                matchesAssignedPlace = null,
                message = "Coordenadas invalidas para consultar cobertura.",
                nearCoverageThresholdMeters = nearCoverageThresholdMeters,
            )
        }

        val detectedPlace = placeRepository.findPlaceContainingPoint(latitude, longitude)
        val detectedPlaceName = detectedPlace?.name?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedAssigned = assignedPlace?.trim()?.takeIf { it.isNotEmpty() }
        val hasCoverage = detectedPlaceName != null
        val matchesAssignedPlace = if (normalizedAssigned != null) {
            detectedPlaceName != null && normalizeZoneKey(detectedPlaceName) == normalizeZoneKey(normalizedAssigned)
        } else {
            null
        }

        val nearestNapBox = findNearestNapBox(latitude, longitude, detectedPlaceName)
        val nearCoverage = !hasCoverage &&
                nearestNapBox != null &&
                nearestNapBox.distanceMeters <= nearCoverageThresholdMeters

        val message = buildCoverageCheckMessage(
            hasCoverage = hasCoverage,
            detectedPlaceName = detectedPlaceName,
            normalizedAssigned = normalizedAssigned,
            matchesAssignedPlace = matchesAssignedPlace,
            nearestNapBox = nearestNapBox,
            nearCoverage = nearCoverage,
        )

        return SmartMapCoverageCheckDto(
            hasCoverage = hasCoverage,
            detectedPlaceName = detectedPlaceName,
            assignedPlaceName = normalizedAssigned,
            matchesAssignedPlace = matchesAssignedPlace,
            message = message,
            nearestNapBox = nearestNapBox,
            nearCoverage = nearCoverage,
            nearCoverageThresholdMeters = nearCoverageThresholdMeters,
        )
    }

    @Transactional(readOnly = true)
    fun buildCollectionRoute(
        place: String,
        collectorLatitude: Double,
        collectorLongitude: Double,
        collectorAccuracyMeters: Double? = null,
        selectedClientIds: Set<Int>? = null,
        visitSince: LocalDateTime? = null,
    ): SmartMapCollectionRouteDto {
        val normalizedPlace = place.trim()
        if (normalizedPlace.isEmpty()) {
            return emptyCollectionRoute(normalizedPlace, collectorLatitude, collectorLongitude, collectorAccuracyMeters)
        }

        if (!isWithinPeruBounds(collectorLatitude, collectorLongitude)) {
            return emptyCollectionRoute(normalizedPlace, collectorLatitude, collectorLongitude, collectorAccuracyMeters)
        }

        val sectorPlace = placeRepository.findByNormalizedName(normalizedPlace).firstOrNull()
        val sectorHasPolygon = sectorPlace?.area != null
        val sectorDebtors = subscriptionRepository.findDebtorsByPlaceName(normalizedPlace)
        val insidePolygonIds = if (sectorHasPolygon) {
            subscriptionRepository.findDebtorIdsInsidePlacePolygon(normalizedPlace).toSet()
        } else {
            null
        }

        val eligibility = evaluateCollectionEligibility(
            subscriptions = sectorDebtors,
            sectorHasPolygon = sectorHasPolygon,
            insidePolygonIds = insidePolygonIds,
            selectedClientIds = selectedClientIds,
        )
        val eligibleClients = eligibility.clients
        val excludedNoGps = eligibility.excludedNoGps
        val excludedOutsidePolygon = eligibility.excludedOutsidePolygon
        val excludedNoDebt = eligibility.excludedNoDebt
        val excludedInactive = eligibility.excludedInactive
        val excludedNotSelected = eligibility.excludedNotSelected
        val excludedNotFound = eligibility.excludedNotFound

        val startPoint = GeoLocationDto(collectorLatitude, collectorLongitude)
        val optimizedStops = optimizeCollectionRoute(startPoint, eligibleClients)
        val stops = buildCollectionRouteStops(
            optimizedClients = optimizedStops,
            collectorLatitude = collectorLatitude,
            collectorLongitude = collectorLongitude,
            visitSince = visitSince,
        )

        val path = buildList {
            add(startPoint)
            addAll(stops.map { it.location })
        }

        val totalDistanceMeters = routeDistanceMeters(path)
        val totalDebt = stops.sumOf { it.totalDebt }
        val excludedReasons = linkedMapOf<String, Int>()
        if (excludedNoGps > 0) excludedReasons["sin_gps_propio"] = excludedNoGps
        if (excludedOutsidePolygon > 0) excludedReasons["fuera_del_poligono"] = excludedOutsidePolygon
        if (excludedNoDebt > 0) excludedReasons["sin_deuda"] = excludedNoDebt
        if (excludedInactive > 0) excludedReasons["no_activo"] = excludedInactive
        if (excludedNotSelected > 0) excludedReasons["no_seleccionado"] = excludedNotSelected
        if (excludedNotFound > 0) excludedReasons["no_encontrado"] = excludedNotFound

        return SmartMapCollectionRouteDto(
            zoneName = normalizedPlace,
            startPoint = startPoint,
            usedCurrentLocation = true,
            stops = stops,
            path = path,
            totalDistanceMeters = totalDistanceMeters,
            totalDebt = totalDebt,
            stopCount = stops.size,
            excludedCount = excludedNoGps + excludedOutsidePolygon + excludedNoDebt + excludedInactive + excludedNotSelected + excludedNotFound,
            excludedReasons = excludedReasons,
            collectorAccuracyMeters = collectorAccuracyMeters,
            sectorHasPolygon = sectorHasPolygon,
            routeType = "sector",
        )
    }

    @Transactional(readOnly = true)
    fun buildCollectionSweepRoute(
        collectorLatitude: Double,
        collectorLongitude: Double,
        collectorAccuracyMeters: Double? = null,
        debtPeriod: String = "LAST_1_MONTH",
        debtDateFrom: LocalDate? = null,
        debtDateTo: LocalDate? = null,
        selectedClientIds: Set<Int>? = null,
        visitSince: LocalDateTime? = null,
    ): SmartMapCollectionRouteDto {
        val sweepFilter = resolveSweepDebtFilter(debtPeriod, debtDateFrom, debtDateTo)
        val periodLabel = buildSweepPeriodLabel(sweepFilter)

        if (!isWithinPeruBounds(collectorLatitude, collectorLongitude)) {
            return emptyCollectionRoute(
                zoneName = "Barrido final",
                collectorLatitude = collectorLatitude,
                collectorLongitude = collectorLongitude,
                collectorAccuracyMeters = collectorAccuracyMeters,
                routeType = "sweep",
            )
        }

        val eligibleClients = resolveEligibleDebtorsForSweep(sweepFilter, selectedClientIds)
        if (eligibleClients.isEmpty()) {
            return emptyCollectionRoute(
                zoneName = "Barrido final ($periodLabel)",
                collectorLatitude = collectorLatitude,
                collectorLongitude = collectorLongitude,
                collectorAccuracyMeters = collectorAccuracyMeters,
                routeType = "sweep",
            )
        }

        val sectorsIncluded = eligibleClients
            .mapNotNull { it.place?.trim()?.takeIf { place -> place.isNotEmpty() } }
            .distinct()
            .sorted()

        val zoneLabel = if (sectorsIncluded.isEmpty()) {
            "Barrido final ($periodLabel)"
        } else {
            "Barrido final ($periodLabel, ${sectorsIncluded.size} sectores)"
        }

        val startPoint = GeoLocationDto(collectorLatitude, collectorLongitude)
        val optimizedStops = optimizeCollectionSweepRoute(startPoint, eligibleClients)
        val stops = buildCollectionRouteStops(
            optimizedClients = optimizedStops,
            collectorLatitude = collectorLatitude,
            collectorLongitude = collectorLongitude,
            visitSince = visitSince,
        )

        val path = buildList {
            add(startPoint)
            addAll(stops.map { it.location })
        }

        return SmartMapCollectionRouteDto(
            zoneName = zoneLabel,
            startPoint = startPoint,
            usedCurrentLocation = true,
            stops = stops,
            path = path,
            totalDistanceMeters = routeDistanceMeters(path),
            totalDebt = stops.sumOf { it.totalDebt },
            stopCount = stops.size,
            excludedCount = selectedClientIds?.let { ids -> (ids - eligibleClients.map { it.id }.toSet()).size } ?: 0,
            excludedReasons = selectedClientIds?.let { ids ->
                val excludedNotFound = (ids - eligibleClients.map { it.id }.toSet()).size
                if (excludedNotFound > 0) mapOf("no_encontrado" to excludedNotFound) else emptyMap()
            } ?: emptyMap(),
            collectorAccuracyMeters = collectorAccuracyMeters,
            sectorHasPolygon = false,
            routeType = "sweep",
            sectorsIncluded = sectorsIncluded,
        )
    }

    @Transactional(readOnly = true)
    fun recalculateCollectionRoute(
        place: String?,
        routeType: String,
        collectorLatitude: Double,
        collectorLongitude: Double,
        collectorAccuracyMeters: Double? = null,
        remainingClientIds: List<Int>,
        debtPeriod: String? = null,
        debtDateFrom: LocalDate? = null,
        debtDateTo: LocalDate? = null,
        visitSince: LocalDateTime? = null,
    ): SmartMapCollectionRouteDto {
        val normalizedRouteType = routeType.trim().lowercase()
        val selectedIds = remainingClientIds.filter { it > 0 }.toSet()
        if (selectedIds.isEmpty()) {
            return emptyCollectionRoute(
                zoneName = place?.trim().orEmpty(),
                collectorLatitude = collectorLatitude,
                collectorLongitude = collectorLongitude,
                collectorAccuracyMeters = collectorAccuracyMeters,
                routeType = normalizedRouteType,
            )
        }

        return if (normalizedRouteType == "sweep") {
            buildCollectionSweepRoute(
                collectorLatitude = collectorLatitude,
                collectorLongitude = collectorLongitude,
                collectorAccuracyMeters = collectorAccuracyMeters,
                debtPeriod = debtPeriod ?: "LAST_1_MONTH",
                debtDateFrom = debtDateFrom,
                debtDateTo = debtDateTo,
                selectedClientIds = selectedIds,
                visitSince = visitSince,
            )
        } else {
            buildCollectionRoute(
                place = place?.trim().orEmpty(),
                collectorLatitude = collectorLatitude,
                collectorLongitude = collectorLongitude,
                collectorAccuracyMeters = collectorAccuracyMeters,
                selectedClientIds = selectedIds,
                visitSince = visitSince,
            )
        }
    }

    private fun buildCollectionRouteStops(
        optimizedClients: List<SmartMapClientDto>,
        collectorLatitude: Double,
        collectorLongitude: Double,
        visitSince: LocalDateTime? = null,
    ): List<SmartMapCollectionRouteStopDto> {
        val clientIds = optimizedClients.map { it.id }
        val latestVisits = collectionVisitService.getLatestVisitsForClients(clientIds, visitSince)

        return optimizedClients.mapIndexed { index, client ->
            val visitLog = latestVisits[client.id]
            SmartMapCollectionRouteStopDto(
                order = index + 1,
                clientId = client.id,
                fullName = listOf(client.firstName, client.lastName).filter { it.isNotBlank() }.joinToString(" ").trim(),
                address = client.address,
                place = client.place,
                totalDebt = client.totalDebt,
                location = client.location,
                facadePhotoUrl = client.facadePhotoUrl,
                visitStatus = CollectionVisitService.mapVisitStatus(visitLog?.status),
                lastVisitAt = visitLog?.visitedAt,
                lastVisitComment = visitLog?.comment,
                distanceFromCollectorMeters = haversineMeters(
                    collectorLatitude,
                    collectorLongitude,
                    client.location.latitude,
                    client.location.longitude,
                ),
            )
        }
    }

    @Transactional(readOnly = true)
    @Cacheable(
        cacheNames = [SmartMapCacheConfiguration.SMART_MAP_SWEEP_PREVIEW_CACHE],
        key = "{#debtPeriod,#debtDateFrom,#debtDateTo}",
    )
    fun getCollectionSweepPreview(
        debtPeriod: String = "LAST_1_MONTH",
        debtDateFrom: LocalDate? = null,
        debtDateTo: LocalDate? = null,
    ): SmartMapCollectionPendingSummaryDto {
        val sweepFilter = resolveSweepDebtFilter(debtPeriod, debtDateFrom, debtDateTo)
        val rows = resolveSweepEligibleRows(sweepFilter)
        val sectors = rows.map { it.placeName }.distinct().sorted()

        return SmartMapCollectionPendingSummaryDto(
            clients = rows.map { row ->
                SmartMapCollectionPendingClientDto(
                    clientId = row.id,
                    fullName = listOf(row.firstName, row.lastName)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                        .trim()
                        .ifBlank { "Cliente ${row.id}" },
                    place = row.placeName,
                    totalDebt = row.totalDebt,
                    markedAt = buildSweepPeriodLabel(sweepFilter),
                )
            },
            clientCount = rows.size,
            sectorCount = sectors.size,
            totalDebt = rows.sumOf { it.totalDebt },
            sectors = sectors,
            debtPeriod = sweepFilter.period,
            debtDateFrom = sweepFilter.dateFrom.toString(),
            debtDateTo = sweepFilter.dateTo.toString(),
            periodLabel = buildSweepPeriodLabel(sweepFilter),
        )
    }

    private data class SweepEligibleDebtorRow(
        val id: Int,
        val firstName: String,
        val lastName: String,
        val placeName: String,
        val totalDebt: Double,
    )

    private data class TimedPolygonCache(
        val ids: Set<Int>,
        val loadedAtMillis: Long,
    )

    private var insidePolygonDebtorIdsCache: TimedPolygonCache? = null

    private fun resolveSweepEligibleRows(
        filter: CollectionSweepDebtFilter,
    ): List<SweepEligibleDebtorRow> {
        val dateFrom = filter.dateFrom.atStartOfDay()
        val dateToExclusive = filter.dateTo.plusDays(1).atStartOfDay()
        val rawRows = subscriptionRepository.findSweepEligibleDebtorRows(dateFrom, dateToExclusive)
        if (rawRows.isEmpty()) {
            return emptyList()
        }

        val polygonPlaceNames = placeRepository.findNormalizedNamesWithPolygon().toSet()
        val insidePolygonIds = loadInsidePolygonDebtorIds()

        return rawRows.mapNotNull { row ->
            mapSweepEligibleDebtorRow(row)
        }.filter { row ->
            val normalizedPlace = normalizeZoneKey(row.placeName)
            normalizedPlace !in polygonPlaceNames || row.id in insidePolygonIds
        }
    }

    private fun mapSweepEligibleDebtorRow(row: Array<Any>): SweepEligibleDebtorRow? {
        if (row.size < 5) {
            return null
        }

        val id = when (val rawId = row[0]) {
            is Number -> rawId.toInt()
            else -> return null
        }
        val firstName = row[1]?.toString()?.trim().orEmpty()
        val lastName = row[2]?.toString()?.trim().orEmpty()
        val placeName = row[3]?.toString()?.trim().orEmpty()
        val totalDebt = when (val rawDebt = row[4]) {
            is Number -> rawDebt.toDouble()
            else -> return null
        }

        if (placeName.isEmpty() || totalDebt <= 0.0) {
            return null
        }

        return SweepEligibleDebtorRow(
            id = id,
            firstName = firstName,
            lastName = lastName,
            placeName = placeName,
            totalDebt = totalDebt,
        )
    }

    private fun loadInsidePolygonDebtorIds(): Set<Int> {
        val now = System.currentTimeMillis()
        val cached = insidePolygonDebtorIdsCache
        if (cached != null && now - cached.loadedAtMillis < SWEEP_POLYGON_CACHE_TTL_MS) {
            return cached.ids
        }

        val ids = subscriptionRepository.findAllDebtorIdsInsidePlacePolygons().toSet()
        insidePolygonDebtorIdsCache = TimedPolygonCache(ids, now)
        return ids
    }

    private fun resolveEligibleDebtorsForSweep(
        filter: CollectionSweepDebtFilter,
        selectedClientIds: Set<Int>? = null,
    ): List<SmartMapClientDto> {
        val eligibleRows = resolveSweepEligibleRows(filter)
            .filter { row -> selectedClientIds == null || row.id in selectedClientIds }
        if (eligibleRows.isEmpty()) {
            return emptyList()
        }

        val subscriptions = subscriptionRepository
            .findDebtorsByIds(eligibleRows.map { it.id })
            .associateBy { it.id }

        return eligibleRows.mapNotNull { row ->
            val subscription = subscriptions[row.id] ?: return@mapNotNull null
            subscription.toSmartMapClientForSweep(filter.dateFrom, filter.dateTo)
        }.distinctBy { it.id }
    }

    private data class CollectionSweepDebtFilter(
        val period: String,
        val dateFrom: LocalDate,
        val dateTo: LocalDate,
    )

    private fun resolveSweepDebtFilter(
        debtPeriod: String,
        debtDateFrom: LocalDate?,
        debtDateTo: LocalDate?,
    ): CollectionSweepDebtFilter {
        val normalizedPeriod = debtPeriod.trim().uppercase().ifBlank { "LAST_1_MONTH" }
        val today = LocalDate.now(ZoneId.systemDefault())

        val (dateFrom, dateTo) = when (normalizedPeriod) {
            "LAST_2_MONTHS" -> today.minusMonths(2) to today
            "CUSTOM" -> {
                val from = debtDateFrom
                    ?: throw IllegalArgumentException("debtDateFrom es requerido para un rango personalizado.")
                val to = debtDateTo
                    ?: throw IllegalArgumentException("debtDateTo es requerido para un rango personalizado.")
                if (from.isAfter(to)) {
                    throw IllegalArgumentException("debtDateFrom no puede ser posterior a debtDateTo.")
                }
                from to to
            }
            else -> today.minusMonths(1) to today
        }

        return CollectionSweepDebtFilter(
            period = normalizedPeriod,
            dateFrom = dateFrom,
            dateTo = dateTo,
        )
    }

    private fun buildSweepPeriodLabel(filter: CollectionSweepDebtFilter): String {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
        return when (filter.period) {
            "LAST_2_MONTHS" -> "ultimos 2 meses"
            "CUSTOM" -> "${filter.dateFrom.format(formatter)} - ${filter.dateTo.format(formatter)}"
            else -> "ultimo mes"
        }
    }

    private data class CollectionEligibilityResult(
        val clients: List<SmartMapClientDto>,
        val excludedNoGps: Int = 0,
        val excludedOutsidePolygon: Int = 0,
        val excludedNoDebt: Int = 0,
        val excludedInactive: Int = 0,
        val excludedNotSelected: Int = 0,
        val excludedNotFound: Int = 0,
    )

    private fun evaluateCollectionEligibility(
        subscriptions: List<Subscription>,
        sectorHasPolygon: Boolean,
        insidePolygonIds: Set<Int>?,
        selectedClientIds: Set<Int>? = null,
    ): CollectionEligibilityResult {
        var excludedNoGps = 0
        var excludedOutsidePolygon = 0
        var excludedNoDebt = 0
        var excludedInactive = 0
        var excludedNotSelected = 0
        val loadedIds = subscriptions.mapNotNull { it.id }.toSet()
        val excludedNotFound = selectedClientIds?.let { (it - loadedIds).size } ?: 0

        val clients = subscriptions.mapNotNull { subscription ->
            val subscriptionId = subscription.id
            if (selectedClientIds != null && subscriptionId !in selectedClientIds) {
                excludedNotSelected += 1
                return@mapNotNull null
            }

            when (
                val mapped = mapSubscriptionToEligibleClient(
                    subscription = subscription,
                    sectorHasPolygon = sectorHasPolygon,
                    insidePolygonIds = insidePolygonIds,
                )
            ) {
                null -> {
                    val pendingDebt = subscription.payments.filter { !it.paid }.sumOf { it.amountToPay }
                    when {
                        subscription.serviceStatus != ServiceStatus.ACTIVE -> excludedInactive += 1
                        pendingDebt <= 0.0 -> excludedNoDebt += 1
                        !subscription.hasOwnGps() -> excludedNoGps += 1
                        sectorHasPolygon && subscription.id !in insidePolygonIds!! -> excludedOutsidePolygon += 1
                    }
                    null
                }
                else -> mapped
            }
        }

        return CollectionEligibilityResult(
            clients = clients,
            excludedNoGps = excludedNoGps,
            excludedOutsidePolygon = excludedOutsidePolygon,
            excludedNoDebt = excludedNoDebt,
            excludedInactive = excludedInactive,
            excludedNotSelected = excludedNotSelected,
            excludedNotFound = excludedNotFound,
        )
    }

    private fun mapSubscriptionToEligibleClient(
        subscription: Subscription,
        sectorHasPolygon: Boolean,
        insidePolygonIds: Set<Int>?,
        debtDateFrom: LocalDate? = null,
        debtDateTo: LocalDate? = null,
    ): SmartMapClientDto? {
        val pendingDebt = if (debtDateFrom != null && debtDateTo != null) {
            subscription.unpaidDebtInRange(debtDateFrom, debtDateTo)
        } else {
            subscription.payments.filter { !it.paid }.sumOf { it.amountToPay }
        }
        if (pendingDebt <= 0.0) {
            return null
        }

        if (subscription.serviceStatus != ServiceStatus.ACTIVE) {
            return null
        }

        if (!subscription.hasOwnGps()) {
            return null
        }

        if (sectorHasPolygon && subscription.id !in insidePolygonIds!!) {
            return null
        }

        return if (debtDateFrom != null && debtDateTo != null) {
            subscription.toSmartMapClientForSweep(debtDateFrom, debtDateTo)
        } else {
            subscription.toSmartMapClient(includeDebt = true)
        }
    }

    private fun emptyCollectionRoute(
        zoneName: String,
        collectorLatitude: Double,
        collectorLongitude: Double,
        collectorAccuracyMeters: Double?,
        routeType: String = "sector",
        sectorsIncluded: List<String> = emptyList(),
    ): SmartMapCollectionRouteDto {
        val startPoint = GeoLocationDto(collectorLatitude, collectorLongitude)
        return SmartMapCollectionRouteDto(
            zoneName = zoneName,
            startPoint = startPoint,
            usedCurrentLocation = true,
            stops = emptyList(),
            path = listOf(startPoint),
            totalDistanceMeters = 0.0,
            totalDebt = 0.0,
            stopCount = 0,
            excludedCount = 0,
            collectorAccuracyMeters = collectorAccuracyMeters,
            routeType = routeType,
            sectorsIncluded = sectorsIncluded,
        )
    }

    private fun optimizeCollectionRoute(
        startPoint: GeoLocationDto,
        clients: List<SmartMapClientDto>,
    ): List<SmartMapClientDto> {
        if (clients.isEmpty()) {
            return emptyList()
        }

        val nearestNeighbor = nearestNeighborOrder(startPoint, clients)
        return twoOptImprove(startPoint, nearestNeighbor)
    }

    /**
     * Barrido multi-sector: completa un sector antes de pasar al siguiente,
     * ordenando sectores por distancia desde el punto de partida del cobrador.
     */
    private fun optimizeCollectionSweepRoute(
        startPoint: GeoLocationDto,
        clients: List<SmartMapClientDto>,
    ): List<SmartMapClientDto> {
        if (clients.isEmpty()) {
            return emptyList()
        }

        val clientsBySector = clients.groupBy { client ->
            client.place?.trim()?.takeIf { place -> place.isNotEmpty() } ?: DEFAULT_ZONE_NAME
        }

        val sectorOrder = clientsBySector.keys.sortedBy { sectorKey ->
            val sectorClients = clientsBySector[sectorKey] ?: emptyList()
            val centroid = sectorCentroid(sectorClients)
            haversineMeters(
                startPoint.latitude,
                startPoint.longitude,
                centroid.latitude,
                centroid.longitude,
            )
        }

        val ordered = mutableListOf<SmartMapClientDto>()
        var currentPoint = startPoint

        sectorOrder.forEach { sectorKey ->
            val sectorClients = clientsBySector[sectorKey] ?: return@forEach
            val sectorStops = optimizeCollectionRoute(currentPoint, sectorClients)
            ordered.addAll(sectorStops)
            if (sectorStops.isNotEmpty()) {
                currentPoint = sectorStops.last().location
            }
        }

        return ordered
    }

    private fun sectorCentroid(clients: List<SmartMapClientDto>): GeoLocationDto {
        if (clients.isEmpty()) {
            return GeoLocationDto(0.0, 0.0)
        }

        val avgLat = clients.map { it.location.latitude }.average()
        val avgLng = clients.map { it.location.longitude }.average()
        return GeoLocationDto(latitude = avgLat, longitude = avgLng)
    }

    private fun nearestNeighborOrder(
        startPoint: GeoLocationDto,
        clients: List<SmartMapClientDto>,
    ): List<SmartMapClientDto> {
        val remaining = clients.toMutableList()
        val ordered = mutableListOf<SmartMapClientDto>()
        var current = startPoint

        while (remaining.isNotEmpty()) {
            val nearestIndex = remaining.indices.minByOrNull { index ->
                val location = remaining[index].location
                haversineMeters(
                    current.latitude,
                    current.longitude,
                    location.latitude,
                    location.longitude,
                )
            } ?: 0

            val nextClient = remaining.removeAt(nearestIndex)
            ordered.add(nextClient)
            current = nextClient.location
        }

        return ordered
    }

    private fun twoOptImprove(
        startPoint: GeoLocationDto,
        orderedClients: List<SmartMapClientDto>,
        maxIterations: Int = 40,
    ): List<SmartMapClientDto> {
        if (orderedClients.size < 4) {
            return orderedClients
        }

        if (orderedClients.size > 100) {
            return orderedClients
        }

        var bestOrder = orderedClients.toMutableList()
        var bestDistance = routeDistanceMeters(buildRoutePath(startPoint, bestOrder))
        var improved = true
        var iterations = 0

        while (improved && iterations < maxIterations) {
            improved = false
            iterations += 1

            for (i in 0 until bestOrder.size - 1) {
                for (j in i + 1 until bestOrder.size) {
                    val candidate = buildList {
                        addAll(bestOrder.subList(0, i))
                        addAll(bestOrder.subList(i, j + 1).asReversed())
                        addAll(bestOrder.subList(j + 1, bestOrder.size))
                    }
                    val candidateDistance = routeDistanceMeters(buildRoutePath(startPoint, candidate))
                    if (candidateDistance + 1.0 < bestDistance) {
                        bestOrder = candidate.toMutableList()
                        bestDistance = candidateDistance
                        improved = true
                    }
                }
            }
        }

        return bestOrder
    }

    private fun buildRoutePath(
        startPoint: GeoLocationDto,
        clients: List<SmartMapClientDto>,
    ): List<GeoLocationDto> = buildList {
        add(startPoint)
        addAll(clients.map { it.location })
    }

    private fun routeDistanceMeters(path: List<GeoLocationDto>): Double {
        if (path.size < 2) {
            return 0.0
        }

        var total = 0.0
        for (index in 1 until path.size) {
            total += haversineMeters(
                path[index - 1].latitude,
                path[index - 1].longitude,
                path[index].latitude,
                path[index].longitude,
            )
        }
        return total
    }

    private fun Subscription.hasOwnGps(): Boolean {
        return location?.let { it.latitude != 0.0 && it.longitude != 0.0 } == true
    }

    private fun findNearestNapBox(
        latitude: Double,
        longitude: Double,
        detectedPlaceName: String?,
    ): SmartMapNearestNapBoxDto? {
        val geolocatedNapBoxes = napBoxRepository.findAll()
            .filter { it.id != null && it.latitude != null && it.longitude != null }

        if (geolocatedNapBoxes.isEmpty()) {
            return null
        }

        val sectorKey = detectedPlaceName?.let { normalizeZoneKey(it) }
        val sectorNapBoxes = if (sectorKey != null) {
            geolocatedNapBoxes.filter { napBox ->
                napBox.place?.name?.trim()?.takeIf { it.isNotEmpty() }?.let { normalizeZoneKey(it) } == sectorKey
            }
        } else {
            emptyList()
        }

        val candidates = sectorNapBoxes.ifEmpty { geolocatedNapBoxes }
        val nearest = candidates.minByOrNull { napBox ->
            haversineMeters(
                latitude,
                longitude,
                napBox.latitude!!.toDouble(),
                napBox.longitude!!.toDouble(),
            )
        } ?: return null

        return nearest.toNearestNapBoxDto(latitude, longitude)
    }

    private fun NapBox.toNearestNapBoxDto(
        prospectLatitude: Double,
        prospectLongitude: Double,
    ): SmartMapNearestNapBoxDto {
        val napBoxId = id!!
        val napLatitude = latitude!!.toDouble()
        val napLongitude = longitude!!.toDouble()
        val distanceMeters = haversineMeters(
            prospectLatitude,
            prospectLongitude,
            napLatitude,
            napLongitude,
        ).toInt()
        val totalPorts = ports_number ?: BorneManagementService.MAX_BORNES_PER_NAP
        val availablePorts = borneManagementService.getAvailableBornes(napBoxId).size

        return SmartMapNearestNapBoxDto(
            id = napBoxId,
            code = code,
            placeName = place?.name?.trim()?.takeIf { it.isNotEmpty() },
            address = address.trim().takeIf { it.isNotEmpty() },
            latitude = napLatitude,
            longitude = napLongitude,
            distanceMeters = distanceMeters,
            availablePorts = availablePorts,
            totalPorts = totalPorts,
        )
    }

    private fun buildCoverageCheckMessage(
        hasCoverage: Boolean,
        detectedPlaceName: String?,
        normalizedAssigned: String?,
        matchesAssignedPlace: Boolean?,
        nearestNapBox: SmartMapNearestNapBoxDto?,
        nearCoverage: Boolean,
    ): String {
        val napSummary = nearestNapBox?.let { formatNearestNapBoxSummary(it) }

        return when {
            hasCoverage && matchesAssignedPlace == false -> buildString {
                append("Hay cobertura en $detectedPlaceName, pero el cliente esta asignado a $normalizedAssigned.")
                if (napSummary != null) {
                    append(" Caja mas cercana: $napSummary.")
                }
            }
            hasCoverage -> buildString {
                append("Hay cobertura en el sector $detectedPlaceName.")
                if (napSummary != null) {
                    append(" Caja mas cercana: $napSummary.")
                }
            }
            nearCoverage -> buildString {
                append("Sin cobertura en el domicilio, pero hay una caja cerca.")
                if (napSummary != null) {
                    append(" $napSummary.")
                }
            }
            napSummary != null -> "No hay cobertura registrada en esta ubicacion. La caja mas cercana es $napSummary."
            else -> "No hay cobertura registrada en esta ubicacion (fuera de poligonos place.area)."
        }
    }

    private fun formatNearestNapBoxSummary(napBox: SmartMapNearestNapBoxDto): String {
        val sectorLabel = napBox.placeName ?: "sin sector"
        return "${napBox.code} ($sectorLabel) a ${napBox.distanceMeters} m con ${napBox.availablePorts} puertos libres"
    }

    private fun isValidCoverageCheckCoordinate(latitude: Double, longitude: Double): Boolean {
        if (latitude == 0.0 && longitude == 0.0) {
            return false
        }
        return latitude in PERU_MIN_LAT..PERU_MAX_LAT && longitude in PERU_MIN_LNG..PERU_MAX_LNG
    }

    private fun loadPlaceCoordinates(): Map<String, GeoLocationDto> {
        return placeRepository.findAll()
            .mapNotNull { place ->
                val name = place.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val latitude = place.latitude?.toDouble() ?: return@mapNotNull null
                val longitude = place.longitude?.toDouble() ?: return@mapNotNull null
                if (latitude == 0.0 || longitude == 0.0) return@mapNotNull null
                normalizeZoneKey(name) to GeoLocationDto(latitude, longitude)
            }
            .toMap()
    }

    private fun loadPlaceAreas(): Map<String, PlaceAreaInfo> {
        return placeRepository.findAll()
            .mapNotNull { place ->
                val name = place.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val geoJson = PlaceGeometryUtils.polygonToGeoJson(place.area) ?: return@mapNotNull null
                val centroid = PlaceGeometryUtils.polygonCentroid(place.area) ?: return@mapNotNull null
                normalizeZoneKey(name) to PlaceAreaInfo(geoJson, centroid)
            }
            .toMap()
    }

    // Nombre canonico legible por clave normalizada, tomado del catalogo de Places.
    private fun loadZoneDisplayNames(): Map<String, String> {
        return placeRepository.findAll()
            .mapNotNull { place ->
                val name = place.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                normalizeZoneKey(name) to name
            }
            .toMap()
    }

    private fun buildZones(
        clients: List<SmartMapClientDto>,
        ticketsByZone: Map<String, Int>,
        includeDebt: Boolean,
        includeTickets: Boolean,
        placeCoordinates: Map<String, GeoLocationDto>,
        placeAreas: Map<String, PlaceAreaInfo>,
        zoneDisplayNames: Map<String, String>,
        geographicPerformance: GeographicPerformanceResumeDto,
        cancellationsByZone: Map<String, Int>,
    ): List<SmartMapZoneDto> {
        // Mapas geograficos reindexados por clave normalizada para casar con las zonas del mapa.
        val densityByKey = geographicPerformance.clientDensityByPlace.normalizeKeys { a, b -> a + b }
        val revenueByKey = geographicPerformance.revenueByPlace.normalizeKeys { a, b -> a + b }
        val growthByKey = geographicPerformance.growthRateByPlace.normalizeKeys { a, b -> maxOf(a, b) }
        val cancellationsByKey = cancellationsByZone.normalizeKeys { a, b -> a + b }

        val accumulators = linkedMapOf<String, ZoneAccumulator>()

        clients.forEach { client ->
            val zoneName = client.place ?: DEFAULT_ZONE_NAME
            val zoneKey = normalizeZoneKey(zoneName)
            val displayName = zoneDisplayNames[zoneKey] ?: zoneName
            val accumulator = accumulators.getOrPut(zoneKey) { ZoneAccumulator(zoneKey, displayName) }

            accumulator.clientCount += 1
            if (client.serviceStatus == ServiceStatus.ACTIVE) {
                accumulator.activeClients += 1
            }

            if (includeDebt) {
                accumulator.totalDebt += client.totalDebt
            }

            val latitude = client.location.latitude
            val longitude = client.location.longitude
            if (latitude != 0.0 && longitude != 0.0) {
                accumulator.clientLocations.add(latitude to longitude)
            }
        }

        if (includeTickets) {
            ticketsByZone.forEach { (zoneKey, ticketCount) ->
                val displayName = zoneDisplayNames[zoneKey] ?: zoneKey.replaceFirstChar { it.uppercase() }
                val accumulator = accumulators.getOrPut(zoneKey) { ZoneAccumulator(zoneKey, displayName) }
                accumulator.openTickets = ticketCount
            }
        }

        return accumulators.values
            .map { it.toDto(placeCoordinates, placeAreas, densityByKey, revenueByKey, growthByKey, cancellationsByKey) }
            .sortedByDescending { it.opportunityScore + it.totalDebt + (it.openTickets * TICKET_SCORE_WEIGHT) }
    }

    private data class PlaceAreaInfo(
        val geoJson: String,
        val centroid: GeoLocationDto,
    )

    private fun buildAlerts(
        zones: List<SmartMapZoneDto>,
        coverageZones: List<CoverageZoneDto>,
    ): List<SmartMapAlertDto> {
        val alerts = mutableListOf<SmartMapAlertDto>()

        zones.filter { it.riskLevel == "critical" && it.totalDebt > 0 }.take(3).forEach { zone ->
            alerts.add(
                SmartMapAlertDto(
                    id = "debt-${zone.zoneName}",
                    severity = "critical",
                    title = "Deuda critica",
                    message = "${zone.zoneName} acumula S/ ${zone.totalDebt.toInt()} en deuda visible.",
                    zoneName = zone.zoneName,
                )
            )
        }

        zones.filter { it.openTickets >= 3 }.take(3).forEach { zone ->
            alerts.add(
                SmartMapAlertDto(
                    id = "tickets-${zone.zoneName}",
                    severity = "warning",
                    title = "Incidencias concentradas",
                    message = "${zone.zoneName} tiene ${zone.openTickets} tickets abiertos.",
                    zoneName = zone.zoneName,
                )
            )
        }

        zones.filter { it.opportunityScore >= 60 }.take(3).forEach { zone ->
            alerts.add(
                SmartMapAlertDto(
                    id = "opportunity-${zone.zoneName}",
                    severity = "info",
                    title = "Oportunidad comercial",
                    message = "${zone.zoneName} muestra alto potencial de expansion (${zone.opportunityScore.toInt()} pts).",
                    zoneName = zone.zoneName,
                )
            )
        }

        coverageZones.filter { it.coverageType == "NONE" && it.status == "ACTIVE" }.take(2).forEach { zone ->
            alerts.add(
                SmartMapAlertDto(
                    id = "coverage-${zone.id}",
                    severity = "warning",
                    title = "Zona sin cobertura",
                    message = "${zone.name} requiere evaluacion de expansion.",
                    zoneName = zone.name,
                )
            )
        }

        return alerts.take(8)
    }

    private fun buildRankings(zones: List<SmartMapZoneDto>): SmartMapRankingsDto {
        fun ranking(
            selector: (SmartMapZoneDto) -> Double,
            labelBuilder: (SmartMapZoneDto) -> String,
        ): List<SmartMapRankingItemDto> {
            return zones
                .sortedByDescending(selector)
                .take(5)
                .map { zone ->
                    SmartMapRankingItemDto(
                        zoneName = zone.zoneName,
                        value = selector(zone),
                        label = labelBuilder(zone),
                    )
                }
        }

        return SmartMapRankingsDto(
            topDebt = ranking({ it.totalDebt }) { "S/ ${it.totalDebt.toInt()}" },
            topIncidence = zones
                .filter { it.clientCount > 0 && it.openTickets > 0 }
                .sortedByDescending { it.openTickets.toDouble() / it.clientCount }
                .take(5)
                .map { zone ->
                    SmartMapRankingItemDto(
                        zoneName = zone.zoneName,
                        value = zone.incidenceRate,
                        label = "${zone.incidenceRate.toInt()}% incidencias",
                    )
                },
            topGrowth = ranking({ it.growthRate }) { "${it.growthRate.toInt()}% crecimiento" },
            topOpportunity = ranking({ it.opportunityScore }) { "${it.opportunityScore.toInt()} pts potencial" },
            topCancellationRisk = zones
                .filter { it.cancellations > 0 }
                .sortedByDescending { it.cancellationRisk }
                .take(5)
                .map { zone ->
                    SmartMapRankingItemDto(
                        zoneName = zone.zoneName,
                        value = zone.cancellationRisk,
                        label = "${zone.cancellations} bajas (${zone.cancellationRisk.toInt()}%)",
                    )
                },
        )
    }

    private fun Subscription.unpaidDebtInRange(
        debtDateFrom: LocalDate,
        debtDateTo: LocalDate,
    ): Double = payments
        .filter { payment -> payment.matchesSweepDebtRange(debtDateFrom, debtDateTo) }
        .sumOf { it.amountToPay }

    private fun Payment.matchesSweepDebtRange(
        debtDateFrom: LocalDate,
        debtDateTo: LocalDate,
    ): Boolean {
        if (paid) {
            return false
        }
        val billingDate = billingDateDatetime.toLocalDate()
        return !billingDate.isBefore(debtDateFrom) && !billingDate.isAfter(debtDateTo)
    }

    private fun Subscription.toSmartMapClientForSweep(
        debtDateFrom: LocalDate,
        debtDateTo: LocalDate,
    ): SmartMapClientDto? {
        if (serviceStatus != ServiceStatus.ACTIVE) {
            return null
        }

        val location = getBestLocation() ?: return null
        val pendingPayments = payments.filter { payment ->
            payment.matchesSweepDebtRange(debtDateFrom, debtDateTo)
        }
        if (pendingPayments.isEmpty()) {
            return null
        }

        return SmartMapClientDto(
            id = id ?: return null,
            firstName = firstName ?: businessName ?: "",
            lastName = if (firstName == null && businessName != null) "" else lastName ?: "",
            plan = plan?.name ?: "Sin plan",
            location = location,
            serviceStatus = serviceStatus,
            address = address,
            phone = phone,
            dni = dni ?: ruc,
            ip = ip,
            subscriptionDate = subscriptionDatetime
                ?.atZone(java.time.ZoneId.systemDefault())
                ?.toInstant()
                ?.toEpochMilli(),
            lastCutOffDate = lastCutOffDate,
            pendingInvoiceQuantity = pendingPayments.size,
            totalDebt = pendingPayments.sumOf { it.amountToPay },
            place = place?.name,
            installationType = installationType?.name,
            locationSource = if (hasOwnGps()) "client_gps" else "place_fallback",
            facadePhotoUrl = facadePhotoUrl,
        )
    }

    private fun Subscription.toSmartMapClient(includeDebt: Boolean): SmartMapClientDto? {
        val location = getBestLocation() ?: return null
        val pendingPayments = if (includeDebt) payments.filter { !it.paid } else emptyList()

        return SmartMapClientDto(
            id = id ?: return null,
            firstName = firstName ?: businessName ?: "",
            lastName = if (firstName == null && businessName != null) "" else lastName ?: "",
            plan = plan?.name ?: "Sin plan",
            location = location,
            serviceStatus = serviceStatus,
            address = address,
            phone = phone,
            dni = dni ?: ruc,
            ip = ip,
            subscriptionDate = subscriptionDatetime
                ?.atZone(java.time.ZoneId.systemDefault())
                ?.toInstant()
                ?.toEpochMilli(),
            lastCutOffDate = lastCutOffDate,
            pendingInvoiceQuantity = pendingPayments.size,
            totalDebt = pendingPayments.sumOf { it.amountToPay },
            place = place?.name,
            installationType = installationType?.name,
            locationSource = if (hasOwnGps()) "client_gps" else "place_fallback",
            facadePhotoUrl = facadePhotoUrl,
        )
    }

    private fun Subscription.matchesFilters(
        search: String?,
        serviceStatuses: List<ServiceStatus>?,
        installationType: String?,
        place: String?,
        plan: String?,
        onlyWithDebt: Boolean
    ): Boolean {
        val matchesStatus = serviceStatuses.isNullOrEmpty() || serviceStatuses.contains(serviceStatus)
        val matchesInstallation = installationType.isNullOrBlank()
                || installationType == "ALL"
                || this.installationType?.name == installationType
        val matchesPlace = place.isNullOrBlank() ||
                normalizeZoneKey(this.place?.name ?: DEFAULT_ZONE_NAME) == normalizeZoneKey(place)
        val matchesPlan = plan.isNullOrBlank() || (this.plan?.name ?: "Sin plan") == plan
        val matchesDebt = !onlyWithDebt || payments.any { !it.paid }
        val matchesSearch = search.isNullOrBlank() || getSearchableText().contains(search.trim().lowercase())

        return matchesStatus
                && matchesInstallation
                && matchesPlace
                && matchesPlan
                && matchesDebt
                && matchesSearch
    }

    private fun Subscription.getSearchableText(): String {
        return listOfNotNull(
            firstName,
            lastName,
            businessName,
            dni,
            ruc,
            plan?.name,
            place?.name,
            address,
            phone,
            ip
        ).joinToString(" ").lowercase()
    }

    private fun Subscription.getBestLocation(): GeoLocationDto? {
        location?.let {
            if (it.latitude != 0.0 && it.longitude != 0.0) {
                return GeoLocationDto(it.latitude, it.longitude)
            }
        }

        val placeLatitude = place?.latitude?.toDouble()
        val placeLongitude = place?.longitude?.toDouble()

        return if (
            placeLatitude != null &&
            placeLongitude != null &&
            placeLatitude != 0.0 &&
            placeLongitude != 0.0
        ) {
            GeoLocationDto(placeLatitude, placeLongitude)
        } else {
            null
        }
    }

    private fun AssistanceTicket.getZoneName(): String {
        return subscription?.place?.name
            ?: placeName
            ?: DEFAULT_ZONE_NAME
    }

    private fun Subscription.matchesSubscriptionDate(dateFrom: LocalDate?, dateTo: LocalDate?): Boolean {
        if (dateFrom == null && dateTo == null) return true
        val date = subscriptionDatetime?.toLocalDate() ?: return false
        if (dateFrom != null && date.isBefore(dateFrom)) return false
        if (dateTo != null && date.isAfter(dateTo)) return false
        return true
    }

    private fun AssistanceTicket.matchesCreatedDate(dateFrom: LocalDate?, dateTo: LocalDate?): Boolean {
        if (dateFrom == null && dateTo == null) return true
        val date = createdAt.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        if (dateFrom != null && date.isBefore(dateFrom)) return false
        if (dateTo != null && date.isAfter(dateTo)) return false
        return true
    }

    // Reindexa un mapa por clave normalizada de zona, combinando colisiones.
    private fun <V> Map<String, V>.normalizeKeys(merge: (V, V) -> V): Map<String, V> {
        val result = linkedMapOf<String, V>()
        forEach { (key, value) ->
            val normalized = normalizeZoneKey(key)
            result[normalized] = result[normalized]?.let { merge(it, value) } ?: value
        }
        return result
    }

    private data class ZoneAccumulator(
        val zoneKey: String,
        val displayName: String,
        var clientCount: Int = 0,
        var activeClients: Int = 0,
        var totalDebt: Double = 0.0,
        var openTickets: Int = 0,
        val clientLocations: MutableList<Pair<Double, Double>> = mutableListOf(),
    ) {
        fun toDto(
            placeCoordinates: Map<String, GeoLocationDto>,
            placeAreas: Map<String, PlaceAreaInfo>,
            densityByKey: Map<String, Int>,
            revenueByKey: Map<String, Double>,
            growthByKey: Map<String, Double>,
            cancellationsByKey: Map<String, Int>,
        ): SmartMapZoneDto {
            val filteredLocations = filterOutlierLocations(clientLocations)
            val hasGeolocatedClients = filteredLocations.isNotEmpty()
            val clientLatitude = filteredLocations.takeIf { it.isNotEmpty() }?.map { it.first }?.average()
            val clientLongitude = filteredLocations.takeIf { it.isNotEmpty() }?.map { it.second }?.average()
            val placeLocation = placeCoordinates[zoneKey]
            val placeArea = placeAreas[zoneKey]

            // Regla de coordenadas: poligono Place.area > clientes GPS > coords de Place > ninguna.
            val latitude: Double?
            val longitude: Double?
            val coordinateSource: String
            when {
                placeArea != null -> {
                    latitude = placeArea.centroid.latitude
                    longitude = placeArea.centroid.longitude
                    coordinateSource = "place_area"
                }
                clientLatitude != null && clientLongitude != null -> {
                    latitude = clientLatitude
                    longitude = clientLongitude
                    coordinateSource = "clients"
                }
                placeLocation != null -> {
                    latitude = placeLocation.latitude
                    longitude = placeLocation.longitude
                    coordinateSource = "place"
                }
                else -> {
                    latitude = null
                    longitude = null
                    coordinateSource = "none"
                }
            }

            val clientDensity = densityByKey[zoneKey] ?: clientCount
            val revenue = revenueByKey[zoneKey] ?: 0.0
            val incidenceRate = if (clientCount > 0) {
                minOf((openTickets.toDouble() / clientCount) * 100.0, 100.0)
            } else {
                0.0
            }
            val growthRate = growthByKey[zoneKey] ?: 0.0
            val opportunityScore = calculateOpportunityScore(clientDensity, growthRate, incidenceRate)
            val cancellations = cancellationsByKey[zoneKey] ?: 0
            val cancellationRisk = when {
                clientCount > 0 -> minOf(100.0, (cancellations.toDouble() / clientCount) * 100.0)
                cancellations > 0 -> 100.0
                else -> 0.0
            }

            return SmartMapZoneDto(
                zoneName = displayName,
                clientCount = clientCount,
                activeClients = activeClients,
                totalDebt = totalDebt,
                openTickets = openTickets,
                riskLevel = getRiskLevel(totalDebt, openTickets, incidenceRate, growthRate),
                debtLevel = getDebtLevel(totalDebt),
                ticketLevel = getTicketLevel(openTickets),
                latitude = latitude,
                longitude = longitude,
                clientDensity = clientDensity,
                revenue = revenue,
                incidenceRate = incidenceRate,
                growthRate = growthRate,
                opportunityScore = opportunityScore,
                displayName = displayName,
                hasGeolocatedClients = hasGeolocatedClients,
                coordinateSource = coordinateSource,
                cancellations = cancellations,
                cancellationRisk = cancellationRisk,
                areaGeoJson = placeArea?.geoJson,
            )
        }
    }

    companion object {
        private const val DEFAULT_ZONE_NAME = "Sin sector"
        private const val SWEEP_POLYGON_CACHE_TTL_MS = 120_000L
        private const val TICKET_SCORE_WEIGHT = 250
        private const val HIGH_DENSITY_THRESHOLD = 15
        private const val PERU_MIN_LAT = -18.5
        private const val PERU_MAX_LAT = 0.0
        private const val PERU_MIN_LNG = -82.0
        private const val PERU_MAX_LNG = -68.0
        private const val MAX_OUTLIER_DISTANCE_METERS = 50000.0

        private fun isWithinPeruBounds(latitude: Double, longitude: Double): Boolean {
            return latitude in PERU_MIN_LAT..PERU_MAX_LAT
                    && longitude in PERU_MIN_LNG..PERU_MAX_LNG
        }

        private fun haversineMeters(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double,
        ): Double {
            val earthRadiusMeters = 6371000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
            return earthRadiusMeters * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        }

        private fun filterOutlierLocations(
            locations: List<Pair<Double, Double>>,
        ): List<Pair<Double, Double>> {
            val validInPeru = locations.filter { (latitude, longitude) ->
                isWithinPeruBounds(latitude, longitude)
            }

            if (validInPeru.size <= 2) {
                return validInPeru
            }

            val medianLat = validInPeru.map { it.first }.sorted()[validInPeru.size / 2]
            val medianLng = validInPeru.map { it.second }.sorted()[validInPeru.size / 2]

            return validInPeru.filter { (latitude, longitude) ->
                haversineMeters(latitude, longitude, medianLat, medianLng) <= MAX_OUTLIER_DISTANCE_METERS
            }
        }

        private fun normalizeZoneKey(zoneName: String): String {
            val normalized = zoneName.trim().lowercase()
            return if (normalized.isEmpty() || normalized == "sin nombre") {
                DEFAULT_ZONE_NAME.lowercase()
            } else {
                normalized
            }
        }

        private fun calculateOpportunityScore(clientDensity: Int, growthRate: Double, incidenceRate: Double): Double {
            val densityFactor = 100.0 / (clientDensity + 10.0)
            val growthFactor = growthRate.coerceAtMost(20.0) * 2.0
            val incidenceFactor = 100.0 / (incidenceRate + 5.0)
            return (densityFactor * 0.35 + growthFactor * 0.35 + incidenceFactor * 0.30)
                .coerceIn(0.0, 100.0)
        }

        private fun hasClientFilters(
            search: String?,
            serviceStatuses: List<ServiceStatus>?,
            installationType: String?,
            place: String?,
            plan: String?,
            onlyWithDebt: Boolean
        ): Boolean {
            val hasStatusFilter = !serviceStatuses.isNullOrEmpty()
                    && serviceStatuses.toSet() != ServiceStatus.values().toSet()

            return !search.isNullOrBlank()
                    || hasStatusFilter
                    || (!installationType.isNullOrBlank() && installationType != "ALL")
                    || !place.isNullOrBlank()
                    || !plan.isNullOrBlank()
                    || onlyWithDebt
        }

        private fun getDebtLevel(totalDebt: Double): String {
            return when {
                totalDebt <= 0.0 -> "none"
                totalDebt <= 500.0 -> "low"
                totalDebt <= 1500.0 -> "medium"
                else -> "high"
            }
        }

        // Semaforo territorial: combina deuda, incidencia (tickets/clientes) y crecimiento negativo.
        private fun getRiskLevel(
            totalDebt: Double,
            openTickets: Int,
            incidenceRate: Double,
            growthRate: Double,
        ): String {
            val criticalIncidence = incidenceRate > 15.0
            val negativeGrowth = growthRate < 0.0
            return when {
                totalDebt > 5000.0 -> "critical"
                criticalIncidence -> "critical"
                totalDebt > 1500.0 && openTickets >= 3 -> "critical"
                totalDebt > 1500.0 -> "watch"
                openTickets >= 2 -> "watch"
                negativeGrowth && totalDebt > 500.0 -> "watch"
                else -> "stable"
            }
        }

        private fun getTicketLevel(openTickets: Int): String {
            return when {
                openTickets <= 0 -> "none"
                openTickets <= 2 -> "low"
                openTickets <= 5 -> "medium"
                else -> "high"
            }
        }
    }
}

private fun AssistanceTicket.toSmartMapTicketDto(): AssistanceTicketDto {
    val priorityLabel = when {
        priority > 5 -> "Alta"
        priority > 2 -> "Media"
        else -> "Baja"
    }
    return AssistanceTicketDto(
        id = id,
        name = subscription?.getFullName() ?: externalCustomerName!!.uppercase(),
        phone = phone,
        category = category,
        description = description,
        status = status,
        comments = comments,
        priority = priorityLabel,
        createdAt = createdAt,
        scheduledAt = scheduledAt,
        assignedAt = assignedAt,
        resolvedAt = resolvedAt,
        closedAt = closedAt,
        assignedTo = if (responsible != null) "${responsible!!.name} ${responsible!!.lastName}" else "",
        place = subscription?.place?.name ?: placeName,
        address = subscription?.address,
        sheetImageUrl = sheetImageUrl,
        isExternalCustomer = isExternalCustomer,
    )
}
