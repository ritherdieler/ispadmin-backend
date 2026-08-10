package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicketStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.dto.GeoLocationDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapClientSearchDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapCollectionRouteDto
import com.dscorp.wispadmin.wispadmin.dto.SmartMapCollectionRouteStopDto
import com.dscorp.wispadmin.wispadmin.dto.TicketRouteLocationDto
import com.dscorp.wispadmin.wispadmin.repository.AssistanceTicketRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.smartmap.CollectionTravelMode
import com.dscorp.wispadmin.wispadmin.smartmap.SmartMapLocationResolver
import kotlinx.coroutines.runBlocking
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Service
class SmartMapCustomRouteService(
    private val subscriptionRepository: SubscriptionRepository,
    private val assistanceTicketRepository: AssistanceTicketRepository,
    private val roadRoutingService: RoadRoutingService,
    private val smartMapRoadRouteService: SmartMapRoadRouteService,
) {

    @Transactional(readOnly = true)
    fun searchCustomers(query: String, limit: Int = DEFAULT_SEARCH_LIMIT): List<SmartMapClientSearchDto> {
        val term = query.trim()
        if (term.length < MIN_QUERY_LENGTH) {
            return emptyList()
        }

        val pageable = PageRequest.of(0, limit.coerceIn(1, MAX_SEARCH_LIMIT))
        val matches = subscriptionRepository.searchForSmartMap(term, pageable).toMutableList()

        // Also resolve exact abonado id when the term is numeric.
        term.toIntOrNull()?.let { id ->
            if (matches.none { it.id == id }) {
                subscriptionRepository.findById(id).orElse(null)?.let { matches.add(it) }
            }
        }

        return matches
            .mapNotNull { it.toSearchDto() }
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<SmartMapClientSearchDto> { it.hasValidLocation }
                    .thenByDescending { it.id },
            )
            .take(limit.coerceIn(1, MAX_SEARCH_LIMIT))
    }

    @Transactional(readOnly = true)
    fun listOpenTicketLocations(place: String? = null): List<TicketRouteLocationDto> {
        val placeFilter = place?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()
        return assistanceTicketRepository.findByStatusIn(OPEN_TICKET_STATUSES)
            .mapNotNull { ticket ->
                val subscription = ticket.subscription ?: return@mapNotNull null
                val location = SmartMapLocationResolver.resolve(subscription) ?: return@mapNotNull null
                val customerId = subscription.id ?: return@mapNotNull null
                val placeName = subscription.place?.name ?: ticket.placeName
                if (placeFilter != null && placeName?.lowercase()?.contains(placeFilter) != true) {
                    return@mapNotNull null
                }
                TicketRouteLocationDto(
                    ticketId = ticket.id,
                    ticketNumber = ticket.id.toString(),
                    subject = ticket.category.ifBlank { ticket.description },
                    customerId = customerId,
                    customerName = subscription.displayCustomerName(),
                    latitude = location.latitude,
                    longitude = location.longitude,
                    placeName = placeName,
                    priority = ticket.priority,
                )
            }
            .sortedWith(compareByDescending<TicketRouteLocationDto> { it.priority }.thenBy { it.ticketId })
    }

    @Transactional(readOnly = true)
    fun buildCustomRoute(
        collectorLatitude: Double,
        collectorLongitude: Double,
        clientIds: List<Int>,
        includeRoadGeometry: Boolean = true,
        preserveManualOrder: Boolean = false,
        travelMode: CollectionTravelMode = CollectionTravelMode.VEHICLE,
        collectorAccuracyMeters: Double? = null,
    ): SmartMapCollectionRouteDto {
        val uniqueIds = clientIds.filter { it > 0 }.distinct()
        val startPoint = GeoLocationDto(collectorLatitude, collectorLongitude)
        if (uniqueIds.isEmpty()) {
            return emptyCustomRoute(startPoint, collectorAccuracyMeters, travelMode)
        }

        val byId = subscriptionRepository.findAllById(uniqueIds).associateBy { it.id }
        val located = uniqueIds.mapNotNull { id ->
            val subscription = byId[id] ?: return@mapNotNull null
            val location = SmartMapLocationResolver.resolve(subscription) ?: return@mapNotNull null
            subscription.toRouteClient(location)
        }

        val ordered = when {
            preserveManualOrder -> located
            else -> optimizeStops(startPoint, located, travelMode)
        }

        val stops = ordered.mapIndexed { index, client ->
            SmartMapCollectionRouteStopDto(
                order = index + 1,
                clientId = client.id,
                fullName = client.fullName,
                address = client.address,
                place = client.place,
                totalDebt = client.debtAmount,
                location = client.location,
                distanceFromCollectorMeters = haversineMeters(
                    collectorLatitude,
                    collectorLongitude,
                    client.location.latitude,
                    client.location.longitude,
                ),
            )
        }

        val path = buildList {
            add(startPoint)
            addAll(stops.map { it.location })
        }

        val base = SmartMapCollectionRouteDto(
            zoneName = "Ruta personalizada",
            startPoint = startPoint,
            usedCurrentLocation = true,
            stops = stops,
            path = path,
            totalDistanceMeters = routeDistanceMeters(path),
            totalDebt = stops.sumOf { it.totalDebt },
            stopCount = stops.size,
            excludedCount = uniqueIds.size - stops.size,
            excludedReasons = if (uniqueIds.size > stops.size) {
                mapOf("sin_ubicacion_o_no_encontrado" to (uniqueIds.size - stops.size))
            } else {
                emptyMap()
            },
            collectorAccuracyMeters = collectorAccuracyMeters,
            routeType = "custom",
            travelMode = travelMode.toApiValue(),
        )

        return if (includeRoadGeometry && base.stopCount > 0) {
            runBlocking { smartMapRoadRouteService.enrichCollectionRoute(base) }
        } else {
            base
        }
    }

    private fun optimizeStops(
        startPoint: GeoLocationDto,
        clients: List<RouteClient>,
        travelMode: CollectionTravelMode,
    ): List<RouteClient> {
        if (clients.size <= 1) return clients
        return try {
            val orderedIndexes = runBlocking {
                roadRoutingService.orderByNearestDuration(
                    origin = startPoint,
                    destinations = clients.map { it.location },
                    travelMode = travelMode,
                )
            }
            if (orderedIndexes.size != clients.size) {
                nearestNeighborOrder(startPoint, clients)
            } else {
                orderedIndexes.map { clients[it] }
            }
        } catch (_: Exception) {
            nearestNeighborOrder(startPoint, clients)
        }
    }

    private fun nearestNeighborOrder(
        startPoint: GeoLocationDto,
        clients: List<RouteClient>,
    ): List<RouteClient> {
        val remaining = clients.toMutableList()
        val ordered = mutableListOf<RouteClient>()
        var current = startPoint
        while (remaining.isNotEmpty()) {
            val nextIndex = remaining.indices.minByOrNull { index ->
                haversineMeters(
                    current.latitude,
                    current.longitude,
                    remaining[index].location.latitude,
                    remaining[index].location.longitude,
                )
            } ?: break
            val next = remaining.removeAt(nextIndex)
            ordered.add(next)
            current = next.location
        }
        return ordered
    }

    private fun Subscription.toSearchDto(): SmartMapClientSearchDto? {
        val id = id ?: return null
        val location = SmartMapLocationResolver.resolve(this)
        return SmartMapClientSearchDto(
            id = id,
            customerName = displayCustomerName(),
            abonadoCode = id.toString(),
            status = serviceStatus,
            address = address,
            latitude = location?.latitude,
            longitude = location?.longitude,
            debtAmount = unpaidDebt(),
            placeName = place?.name,
            hasValidLocation = location != null,
        )
    }

    private fun Subscription.toRouteClient(location: GeoLocationDto): RouteClient {
        return RouteClient(
            id = id!!,
            fullName = displayCustomerName(),
            address = address,
            place = place?.name,
            debtAmount = unpaidDebt(),
            location = location,
        )
    }

    private fun Subscription.displayCustomerName(): String {
        val person = listOfNotNull(firstName, lastName).joinToString(" ").trim()
        return when {
            person.isNotBlank() -> person
            !businessName.isNullOrBlank() -> businessName!!
            else -> "Cliente ${id ?: "?"}"
        }
    }

    private fun Subscription.unpaidDebt(): Double =
        payments.filter { !it.paid }.sumOf { it.amountToPay }

    private fun emptyCustomRoute(
        startPoint: GeoLocationDto,
        collectorAccuracyMeters: Double?,
        travelMode: CollectionTravelMode,
    ): SmartMapCollectionRouteDto = SmartMapCollectionRouteDto(
        zoneName = "Ruta personalizada",
        startPoint = startPoint,
        usedCurrentLocation = true,
        stops = emptyList(),
        path = listOf(startPoint),
        totalDistanceMeters = 0.0,
        totalDebt = 0.0,
        stopCount = 0,
        excludedCount = 0,
        collectorAccuracyMeters = collectorAccuracyMeters,
        routeType = "custom",
        travelMode = travelMode.toApiValue(),
    )

    private fun routeDistanceMeters(path: List<GeoLocationDto>): Double {
        if (path.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until path.lastIndex) {
            total += haversineMeters(
                path[i].latitude,
                path[i].longitude,
                path[i + 1].latitude,
                path[i + 1].longitude,
            )
        }
        return total
    }

    private fun haversineMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    private data class RouteClient(
        val id: Int,
        val fullName: String,
        val address: String?,
        val place: String?,
        val debtAmount: Double,
        val location: GeoLocationDto,
    )

    companion object {
        const val MIN_QUERY_LENGTH = 2
        const val DEFAULT_SEARCH_LIMIT = 20
        const val MAX_SEARCH_LIMIT = 50

        /** Open tickets for custom routing. Domain has no OPEN enum — use pending/assigned/reopen/in-progress. */
        val OPEN_TICKET_STATUSES = listOf(
            AssistanceTicketStatus.PENDING,
            AssistanceTicketStatus.ASSIGNED,
            AssistanceTicketStatus.REOPEN,
            AssistanceTicketStatus.IN_PROGRESS,
        )
    }
}
