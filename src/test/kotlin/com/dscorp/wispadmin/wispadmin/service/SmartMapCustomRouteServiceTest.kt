package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicket
import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicketStatus
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.GeoLocation
import com.dscorp.wispadmin.wispadmin.data.model.Payment
import com.dscorp.wispadmin.wispadmin.data.model.Place
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.AssistanceTicketRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.smartmap.ClientLocationRules
import com.dscorp.wispadmin.wispadmin.smartmap.CollectionTravelMode
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SmartMapCustomRouteServiceTest {

    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val assistanceTicketRepository = mockk<AssistanceTicketRepository>()
    private val roadRoutingService = mockk<RoadRoutingService>()
    private val smartMapRoadRouteService = mockk<SmartMapRoadRouteService>(relaxed = true)

    private lateinit var service: SmartMapCustomRouteService

    @BeforeEach
    fun setUp() {
        service = SmartMapCustomRouteService(
            subscriptionRepository = subscriptionRepository,
            assistanceTicketRepository = assistanceTicketRepository,
            roadRoutingService = roadRoutingService,
            smartMapRoadRouteService = smartMapRoadRouteService,
        )
    }

    @Test
    fun `searchCustomers returns active and cancelled clients with valid coordinates`() {
        val active = subscription(
            id = 1,
            firstName = "Ana",
            lastName = "Lopez",
            dni = "12345678",
            status = ServiceStatus.ACTIVE,
            location = GeoLocation(-11.24, -77.38),
        )
        val cancelled = subscription(
            id = 2,
            firstName = "Ana",
            lastName = "Perez",
            dni = "87654321",
            status = ServiceStatus.CANCELLED,
            location = null,
            place = Place(id = 1, name = "Tiw", latitude = -11.25f, longitude = -77.39f),
        )
        every { subscriptionRepository.searchForSmartMap(any(), any()) } returns listOf(active, cancelled)

        val result = service.searchCustomers("Ana")

        assertEquals(2, result.size)
        assertTrue(result.any { it.status == ServiceStatus.ACTIVE && it.id == 1 })
        assertTrue(result.any { it.status == ServiceStatus.CANCELLED && it.id == 2 })
        assertEquals(-11.24, result.first { it.id == 1 }.latitude!!, 1e-6)
        assertEquals(-11.25, result.first { it.id == 2 }.latitude!!, 1e-6)
        assertTrue(result.all { it.hasValidLocation })
    }

    @Test
    fun `searchCustomers returns hasValidLocation false when coordinates are missing`() {
        val invalidGps = subscription(
            id = 3,
            firstName = "Sin",
            lastName = "Gps",
            dni = "71822055",
            status = ServiceStatus.ACTIVE,
            location = GeoLocation(ClientLocationRules.DEFAULT_LAT, ClientLocationRules.DEFAULT_LNG),
            place = null,
        )
        every { subscriptionRepository.searchForSmartMap(any(), any()) } returns listOf(invalidGps)
        every { subscriptionRepository.findById(71822055) } returns java.util.Optional.empty()

        val result = service.searchCustomers("71822055")

        assertEquals(1, result.size)
        assertEquals(3, result[0].id)
        assertEquals(false, result[0].hasValidLocation)
        assertEquals(null, result[0].latitude)
        assertEquals(null, result[0].longitude)
    }

    @Test
    fun `searchCustomers returns empty list for short or blank query`() {
        assertTrue(service.searchCustomers(" ").isEmpty())
        assertTrue(service.searchCustomers("a").isEmpty())
        verify(exactly = 0) { subscriptionRepository.searchForSmartMap(any(), any()) }
    }

    @Test
    fun `listOpenTicketLocations resolves coordinates from subscription`() {
        val subscription = subscription(
            id = 10,
            firstName = "Carlos",
            lastName = "Ruiz",
            status = ServiceStatus.CUT_OFF,
            location = GeoLocation(-11.26, -77.40),
            place = Place(id = 2, name = "La Villa", latitude = -11.20f, longitude = -77.30f),
        )
        val ticket = AssistanceTicket(
            id = 55,
            phone = "51999999999",
            category = "Avería",
            description = "Sin internet",
            status = AssistanceTicketStatus.PENDING,
            priority = 2,
            subscription = subscription,
            placeName = "La Villa",
        )
        every {
            assistanceTicketRepository.findByStatusIn(SmartMapCustomRouteService.OPEN_TICKET_STATUSES)
        } returns listOf(ticket)

        val result = service.listOpenTicketLocations()

        assertEquals(1, result.size)
        assertEquals(55, result[0].ticketId)
        assertEquals("55", result[0].ticketNumber)
        assertEquals("Avería", result[0].subject)
        assertEquals(10, result[0].customerId)
        assertEquals(-11.26, result[0].latitude, 1e-6)
        assertEquals(-77.40, result[0].longitude, 1e-6)
        assertEquals(2, result[0].priority)
    }

    @Test
    fun `listOpenTicketLocations skips tickets without locatable subscription`() {
        val ticket = AssistanceTicket(
            id = 56,
            phone = "51988888888",
            category = "Consulta",
            description = "Externo",
            status = AssistanceTicketStatus.ASSIGNED,
            priority = 1,
            subscription = null,
            isExternalCustomer = true,
            externalCustomerName = "Externo",
        )
        every {
            assistanceTicketRepository.findByStatusIn(SmartMapCustomRouteService.OPEN_TICKET_STATUSES)
        } returns listOf(ticket)

        assertTrue(service.listOpenTicketLocations().isEmpty())
    }

    @Test
    fun `buildCustomRoute preserves manual order without calling matrix`() {
        val near = subscription(
            id = 1,
            firstName = "Cerca",
            lastName = "Uno",
            status = ServiceStatus.CANCELLED,
            location = GeoLocation(-11.230, -77.370),
        )
        val far = subscription(
            id = 2,
            firstName = "Lejos",
            lastName = "Dos",
            status = ServiceStatus.ACTIVE,
            location = GeoLocation(-11.250, -77.390),
        )
        every { subscriptionRepository.findAllById(listOf(2, 1)) } returns listOf(far, near)

        val route = service.buildCustomRoute(
            collectorLatitude = -11.220,
            collectorLongitude = -77.360,
            clientIds = listOf(2, 1),
            includeRoadGeometry = false,
            preserveManualOrder = true,
            travelMode = CollectionTravelMode.VEHICLE,
        )

        assertEquals(2, route.stopCount)
        assertEquals(2, route.stops[0].clientId)
        assertEquals(1, route.stops[1].clientId)
        assertEquals("custom", route.routeType)
        coVerify(exactly = 0) { smartMapRoadRouteService.enrichCollectionRoute(any()) }
    }

    private fun subscription(
        id: Int,
        firstName: String,
        lastName: String,
        status: ServiceStatus,
        location: GeoLocation?,
        place: Place? = null,
        dni: String? = null,
        payments: MutableSet<Payment> = mutableSetOf(),
    ): Subscription = Subscription(
        id = id,
        firstName = firstName,
        lastName = lastName,
        dni = dni,
        serviceStatus = status,
        location = location,
        place = place,
        payments = payments,
        address = "Calle $id",
        subscriptionDatetime = LocalDateTime.now().minusMonths(2),
        equipmentCondition = EquipmentCondition.LOAN,
    )
}
