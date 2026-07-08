package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicket
import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicketStatus
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
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
import com.dscorp.wispadmin.wispadmin.dto.toDto
import com.dscorp.wispadmin.wispadmin.repository.AssistanceTicketRepository
import com.dscorp.wispadmin.wispadmin.repository.CommercialOpportunityRepository
import com.dscorp.wispadmin.wispadmin.repository.CoverageZoneRepository
import com.dscorp.wispadmin.wispadmin.repository.PlaceRepository
import com.dscorp.wispadmin.wispadmin.repository.SalesLeadMapRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SmartMapService(
    private val subscriptionRepository: SubscriptionRepository,
    private val assistanceTicketRepository: AssistanceTicketRepository,
    private val placeRepository: PlaceRepository,
    private val dashBoardService: DashBoardService,
    private val coverageZoneRepository: CoverageZoneRepository,
    private val salesLeadMapRepository: SalesLeadMapRepository,
    private val commercialOpportunityRepository: CommercialOpportunityRepository,
) {

    @Transactional(readOnly = true)
    fun getSummary(
        includeDebt: Boolean = true,
        includeTickets: Boolean = true,
        search: String? = null,
        serviceStatuses: List<ServiceStatus>? = null,
        installationType: String? = null,
        place: String? = null,
        plan: String? = null,
        onlyWithDebt: Boolean = false,
        startDate: Long? = null,
        endDate: Long? = null,
    ): SmartMapSummaryDto {
        val hasClientFilters = hasClientFilters(
            search = search,
            serviceStatuses = serviceStatuses,
            installationType = installationType,
            place = place,
            plan = plan,
            onlyWithDebt = onlyWithDebt,
            startDate = startDate,
            endDate = endDate,
        )

        val clients = subscriptionRepository.findAll()
            .filter {
                it.matchesFilters(
                    search = search,
                    serviceStatuses = serviceStatuses,
                    installationType = installationType,
                    place = place,
                    plan = plan,
                    onlyWithDebt = onlyWithDebt,
                    startDate = startDate,
                    endDate = endDate,
                )
            }
            .mapNotNull { it.toSmartMapClient(includeDebt) }

        val placeCatalog = loadPlaceCatalog()

        val ticketsByZone = if (includeTickets) {
            val visibleZoneKeys = if (hasClientFilters) {
                place?.takeIf { it.isNotBlank() }?.let { setOf(normalizeZoneKey(it)) }
                    ?: clients.map { normalizeZoneKey(it.place ?: DEFAULT_ZONE_NAME) }.toSet()
            } else {
                emptySet()
            }

            assistanceTicketRepository.findByStatusIn(
                listOf(AssistanceTicketStatus.PENDING, AssistanceTicketStatus.ASSIGNED)
            )
                .filter { ticket ->
                    !hasClientFilters || visibleZoneKeys.contains(normalizeZoneKey(ticket.getZoneName()))
                }
                .groupingBy { normalizeZoneKey(it.getZoneName()) }
                .eachCount()
        } else {
            emptyMap()
        }

        val geographicPerformance = dashBoardService.getGeographicPerformanceData()
        val lifecycleData = dashBoardService.getClientLifecycleData()
        val zones = buildZones(
            clients,
            ticketsByZone,
            includeDebt,
            includeTickets,
            placeCatalog,
            geographicPerformance,
            lifecycleData.cancellationsByZone,
        )
        val openTickets = if (includeTickets) zones.sumOf { it.openTickets } else 0
        val totalDebt = if (includeDebt) clients.sumOf { it.totalDebt } else 0.0

        val coverageZones = coverageZoneRepository.findAll().map { it.toDto() }
        val salesLeads = salesLeadMapRepository.findAll().map { it.toDto() }
        val commercialOpportunities = commercialOpportunityRepository.findAll().map { it.toDto() }
        val alerts = buildAlerts(zones, coverageZones)
        val rankings = buildRankings(zones, lifecycleData.cancellationsByZone)

        return SmartMapSummaryDto(
            clients = clients,
            zones = zones,
            kpis = SmartMapKpisDto(
                totalClients = clients.size,
                activeClients = clients.count { it.serviceStatus == ServiceStatus.ACTIVE },
                geolocatedClients = clients.size,
                totalDebt = totalDebt,
                totalZones = zones.size,
                openTickets = openTickets
            ),
            coverageZones = coverageZones,
            salesLeads = salesLeads,
            commercialOpportunities = commercialOpportunities,
            alerts = alerts,
            rankings = rankings,
        )
    }

    private data class PlaceCatalog(
        val canonicalNames: Map<String, String>,
        val coordinates: Map<String, GeoLocationDto>,
    )

    private fun loadPlaceCatalog(): PlaceCatalog {
        val canonicalNames = linkedMapOf<String, String>()
        val coordinates = linkedMapOf<String, GeoLocationDto>()

        placeRepository.findAll().forEach { place ->
            val name = place.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            val key = normalizeZoneKey(name)
            canonicalNames[key] = name
            val latitude = place.latitude?.toDouble() ?: return@forEach
            val longitude = place.longitude?.toDouble() ?: return@forEach
            if (latitude == 0.0 || longitude == 0.0) return@forEach
            coordinates[key] = GeoLocationDto(latitude, longitude)
        }

        return PlaceCatalog(canonicalNames, coordinates)
    }

    private fun resolveDisplayName(rawName: String, catalog: PlaceCatalog): String {
        val trimmed = rawName.trim().ifEmpty { DEFAULT_ZONE_NAME }
        return catalog.canonicalNames[normalizeZoneKey(trimmed)] ?: trimmed
    }

    private fun <T> resolveGeographicValue(
        values: Map<String, T>,
        zoneName: String,
    ): T? {
        values[zoneName]?.let { return it }

        val normalizedZone = normalizeZoneKey(zoneName)
        values.entries.firstOrNull { normalizeZoneKey(it.key) == normalizedZone }?.value?.let { return it }

        if (normalizedZone == normalizeZoneKey(DEFAULT_ZONE_NAME)) {
            return values["Sin nombre"]
        }

        return null
    }

    private fun buildZones(
        clients: List<SmartMapClientDto>,
        ticketsByZone: Map<String, Int>,
        includeDebt: Boolean,
        includeTickets: Boolean,
        placeCatalog: PlaceCatalog,
        geographicPerformance: GeographicPerformanceResumeDto,
        cancellationsByZone: Map<String, Int>,
    ): List<SmartMapZoneDto> {
        val accumulators = linkedMapOf<String, ZoneAccumulator>()

        clients.forEach { client ->
            val rawName = client.place ?: DEFAULT_ZONE_NAME
            val zoneKey = normalizeZoneKey(rawName)
            val displayName = resolveDisplayName(rawName, placeCatalog)
            val accumulator = accumulators.getOrPut(zoneKey) { ZoneAccumulator(zoneKey, displayName) }

            accumulator.clientCount += 1
            if (client.serviceStatus == ServiceStatus.ACTIVE) {
                accumulator.activeClients += 1
            }

            if (includeDebt) {
                accumulator.totalDebt += client.totalDebt
            }

            accumulator.latitudeSum += client.location.latitude
            accumulator.longitudeSum += client.location.longitude
            accumulator.locatedClients += 1
        }

        if (includeTickets) {
            ticketsByZone.forEach { (zoneKey, ticketCount) ->
                val displayName = placeCatalog.canonicalNames[zoneKey]
                    ?: accumulators[zoneKey]?.displayName
                    ?: zoneKey
                val accumulator = accumulators.getOrPut(zoneKey) {
                    ZoneAccumulator(zoneKey, displayName)
                }
                accumulator.openTickets = ticketCount
            }
        }

        return accumulators.values
            .map { it.toDto(placeCatalog, geographicPerformance, cancellationsByZone) }
            .sortedByDescending { it.opportunityScore + it.totalDebt + (it.openTickets * TICKET_SCORE_WEIGHT) }
    }

    private fun buildAlerts(
        zones: List<SmartMapZoneDto>,
        coverageZones: List<CoverageZoneDto>,
    ): List<SmartMapAlertDto> {
        val alerts = mutableListOf<SmartMapAlertDto>()

        zones.filter { it.riskLevel == "critical" }.take(3).forEach { zone ->
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

    private fun buildRankings(
        zones: List<SmartMapZoneDto>,
        cancellationsByZone: Map<String, Int>,
    ): SmartMapRankingsDto {
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
                .map { zone ->
                    val cancellations = resolveGeographicValue(cancellationsByZone, zone.zoneName) ?: 0
                    val risk = if (zone.clientCount > 0) {
                        (cancellations.toDouble() / zone.clientCount) * 100.0
                    } else {
                        cancellations.toDouble() * 10.0
                    }
                    zone.copy(cancellationRisk = risk.coerceAtMost(100.0))
                }
                .sortedByDescending { it.cancellationRisk }
                .take(5)
                .map { zone ->
                    SmartMapRankingItemDto(
                        zoneName = zone.zoneName,
                        value = zone.cancellationRisk,
                        label = "${zone.cancellationRisk.toInt()}% riesgo",
                    )
                },
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
            installationType = installationType?.name
        )
    }

    private fun Subscription.matchesFilters(
        search: String?,
        serviceStatuses: List<ServiceStatus>?,
        installationType: String?,
        place: String?,
        plan: String?,
        onlyWithDebt: Boolean,
        startDate: Long? = null,
        endDate: Long? = null,
    ): Boolean {
        val matchesStatus = serviceStatuses.isNullOrEmpty() || serviceStatuses.contains(serviceStatus)
        val matchesInstallation = installationType.isNullOrBlank()
                || installationType == "ALL"
                || this.installationType?.name == installationType
        val matchesPlace = place.isNullOrBlank()
                || normalizeZoneKey(this.place?.name ?: DEFAULT_ZONE_NAME) == normalizeZoneKey(place)
        val matchesPlan = plan.isNullOrBlank() || (this.plan?.name ?: "Sin plan") == plan
        val matchesDebt = !onlyWithDebt || payments.any { !it.paid }
        val matchesSearch = search.isNullOrBlank() || getSearchableText().contains(search.trim().lowercase())
        val matchesDate = when {
            startDate == null && endDate == null -> true
            subscriptionDatetime == null -> false
            else -> {
                val subscriptionMillis = subscriptionDatetime
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                (startDate == null || subscriptionMillis >= startDate)
                        && (endDate == null || subscriptionMillis <= endDate)
            }
        }

        return matchesStatus
                && matchesInstallation
                && matchesPlace
                && matchesPlan
                && matchesDebt
                && matchesSearch
                && matchesDate
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

    private data class ZoneAccumulator(
        val zoneKey: String,
        var displayName: String,
        var clientCount: Int = 0,
        var activeClients: Int = 0,
        var totalDebt: Double = 0.0,
        var openTickets: Int = 0,
        var latitudeSum: Double = 0.0,
        var longitudeSum: Double = 0.0,
        var locatedClients: Int = 0
    ) {
        fun toDto(
            placeCatalog: PlaceCatalog,
            geographicPerformance: GeographicPerformanceResumeDto,
            cancellationsByZone: Map<String, Int>,
        ): SmartMapZoneDto {
            val clientLatitude = if (locatedClients > 0) latitudeSum / locatedClients else null
            val clientLongitude = if (locatedClients > 0) longitudeSum / locatedClients else null
            val placeLocation = placeCatalog.coordinates[zoneKey]
            val latitude = clientLatitude ?: placeLocation?.latitude
            val longitude = clientLongitude ?: placeLocation?.longitude

            val clientDensity = resolveGeographicValue(geographicPerformance.clientDensityByPlace, displayName)
                ?: clientCount
            val revenue = resolveGeographicValue(geographicPerformance.revenueByPlace, displayName) ?: 0.0
            val incidenceRate = if (clientCount > 0) {
                minOf((openTickets.toDouble() / clientCount) * 100.0, 100.0)
            } else {
                resolveGeographicValue(geographicPerformance.incidenceRateByPlace, displayName) ?: 0.0
            }
            val growthRate = resolveGeographicValue(geographicPerformance.growthRateByPlace, displayName) ?: 0.0
            val opportunityScore = calculateOpportunityScore(clientDensity, growthRate, incidenceRate)
            val cancellations = resolveGeographicValue(cancellationsByZone, displayName) ?: 0
            val cancellationRisk = if (clientCount > 0) {
                minOf((cancellations.toDouble() / clientCount) * 100.0, 100.0)
            } else {
                0.0
            }

            return SmartMapZoneDto(
                zoneName = displayName,
                displayName = displayName,
                clientCount = clientCount,
                activeClients = activeClients,
                totalDebt = totalDebt,
                openTickets = openTickets,
                riskLevel = getRiskLevel(totalDebt, openTickets, incidenceRate),
                debtLevel = getDebtLevel(totalDebt),
                ticketLevel = getTicketLevel(openTickets),
                latitude = latitude,
                longitude = longitude,
                clientDensity = clientDensity,
                revenue = revenue,
                incidenceRate = incidenceRate,
                growthRate = growthRate,
                opportunityScore = opportunityScore,
                cancellationRisk = cancellationRisk,
            )
        }
    }

    companion object {
        private const val DEFAULT_ZONE_NAME = "Sin sector"
        private const val TICKET_SCORE_WEIGHT = 250

        private fun normalizeZoneKey(zoneName: String): String = zoneName.trim().lowercase()

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
            onlyWithDebt: Boolean,
            startDate: Long? = null,
            endDate: Long? = null,
        ): Boolean {
            val hasStatusFilter = !serviceStatuses.isNullOrEmpty()
                    && serviceStatuses.toSet() != ServiceStatus.values().toSet()

            return !search.isNullOrBlank()
                    || hasStatusFilter
                    || (!installationType.isNullOrBlank() && installationType != "ALL")
                    || !place.isNullOrBlank()
                    || !plan.isNullOrBlank()
                    || onlyWithDebt
                    || startDate != null
                    || endDate != null
        }

        private fun getDebtLevel(totalDebt: Double): String {
            return when {
                totalDebt <= 0.0 -> "none"
                totalDebt <= 500.0 -> "low"
                totalDebt <= 1500.0 -> "medium"
                else -> "high"
            }
        }

        private fun getRiskLevel(totalDebt: Double, openTickets: Int, incidenceRate: Double): String {
            val debtScore = when {
                totalDebt > 1500.0 -> 3
                totalDebt > 500.0 -> 2
                totalDebt > 0.0 -> 1
                else -> 0
            }
            val ticketScore = when {
                openTickets >= 6 -> 3
                openTickets >= 3 -> 2
                openTickets > 0 -> 1
                else -> 0
            }
            val incidenceScore = when {
                incidenceRate >= 50.0 -> 3
                incidenceRate >= 25.0 -> 2
                incidenceRate > 0.0 -> 1
                else -> 0
            }
            val combined = debtScore + ticketScore + incidenceScore

            return when {
                combined >= 6 -> "critical"
                combined >= 3 -> "watch"
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
