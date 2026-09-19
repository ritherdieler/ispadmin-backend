package com.dscorp.wispadmin.wispadmin.service.genieacs

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus
import com.dscorp.wispadmin.wispadmin.dto.AcsLinkStatus
import com.dscorp.wispadmin.wispadmin.oltclient.OltGatewayHttpClient
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.search.application.SubscriptionChangedEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SubscriptionAcsLinkServiceTest {

    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val client = mockk<GenieAcsClient>()
    private val syncService = mockk<SubscriptionAcsSyncService>(relaxed = true)
    private val tagger = mockk<GenieAcsSubscriptionTagger>(relaxed = true)
    private val gatewayHttp = mockk<ObjectProvider<OltGatewayHttpClient>>()
    private val gateway = mockk<OltGatewayHttpClient>()
    private val events = mockk<ApplicationEventPublisher>(relaxed = true)
    private val clock: Clock = Clock.fixed(Instant.parse("2026-09-19T16:00:00Z"), ZoneOffset.UTC)
    private lateinit var service: SubscriptionAcsLinkService

    @BeforeEach
    fun setUp() {
        every { gatewayHttp.ifAvailable } returns gateway
        every { gateway.getJson(any(), any()) } returns ResponseEntity.ok("""{"onus":[{"sn":"VSOL0086D819"}],"status":true}""")
        every { gateway.getJson(any()) } returns ResponseEntity.ok("""{"onus":[{"sn":"VSOL0086D819"}],"status":true}""")
        every { gateway.postJsonBody(any(), any()) } returns ResponseEntity.ok("{}")
        every { subscriptionRepository.save(any()) } answers { firstArg() }
        service = SubscriptionAcsLinkService(
            subscriptionRepository = subscriptionRepository,
            client = client,
            syncService = syncService,
            tagger = tagger,
            gatewayHttp = gatewayHttp,
            eventPublisher = events,
        )
        service.clock = clock
    }

    @Test
    fun `one ACTIVE with fresh Inform writes device id COMPLETE tags and ensure-mgmt without provision`() {
        val device = vsolDevice(lastInform = "2026-09-19T15:00:00.000Z")
        every { client.findDeviceById(DEVICE_ID) } returns device
        val subscription = holder(2070, "VSOL0086D819", ServiceStatus.ACTIVE)
        every { subscriptionRepository.findByOnuSerialOrSuffix(any(), "86D819") } returns listOf(subscription)

        val result = service.link(DEVICE_ID)

        assertEquals(AcsLinkStatus.LINKED, result.status)
        assertEquals(2070, result.subscriptionId)
        assertEquals(DEVICE_ID, subscription.tr069DeviceId)
        assertEquals(Tr069ProvisionStatus.COMPLETE, subscription.tr069ProvisionStatus)
        assertEquals("VSOL0086D819", subscription.fiberOnuSn)
        val outcome = slot<Tr069ProvisionOutcome>()
        verify { syncService.upsertFromProvision(eq(2070), capture(outcome), eq("VSOL0086D819")) }
        assertEquals(Tr069ProvisionStatus.COMPLETE, outcome.captured.status)
        assertEquals(DEVICE_ID, outcome.captured.deviceId)
        verify {
            tagger.apply(
                deviceId = DEVICE_ID,
                subscriptionId = 2070,
                kind = any(),
                fullName = any(),
                previousDeviceId = null,
            )
        }
        verify { gateway.postJsonBody("/api/olt-gateway/onus/VSOL0086D819/service-port/ensure-mgmt", """{"vlan":1000}""") }
        verify { events.publishEvent(SubscriptionChangedEvent(2070)) }
        verify(exactly = 0) { client.setParameterValues(any(), any(), any()) }
        verify(exactly = 0) { client.setParameterValuesPrivate(any(), any(), any()) }
    }

    @Test
    fun `stale Inform links without COMPLETE`() {
        val device = vsolDevice(lastInform = "2026-09-01T00:00:00.000Z")
        every { client.findDeviceById(DEVICE_ID) } returns device
        val subscription = holder(2070, "VSOL0086D819", ServiceStatus.ACTIVE)
        every { subscriptionRepository.findByOnuSerialOrSuffix(any(), "86D819") } returns listOf(subscription)

        val result = service.link(DEVICE_ID)

        assertEquals(AcsLinkStatus.LINKED, result.status)
        assertEquals(DEVICE_ID, subscription.tr069DeviceId)
        assertNull(subscription.tr069ProvisionStatus)
        val outcome = slot<Tr069ProvisionOutcome>()
        verify { syncService.upsertFromProvision(eq(2070), capture(outcome), any()) }
        assertEquals(Tr069ProvisionStatus.PENDING, outcome.captured.status)
    }

    @Test
    fun `no subscription is SKIP_NONE and does not write`() {
        every { client.findDeviceById(DEVICE_ID) } returns vsolDevice()
        every { subscriptionRepository.findByOnuSerialOrSuffix(any(), "86D819") } returns emptyList()

        val result = service.link(DEVICE_ID)

        assertEquals(AcsLinkStatus.SKIP_NONE, result.status)
        assertNull(result.subscriptionId)
        verify(exactly = 0) { subscriptionRepository.save(any()) }
        verify(exactly = 0) { syncService.upsertFromProvision(any(), any(), any()) }
        verify(exactly = 0) { tagger.apply(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `two occupied subscriptions are SKIP_AMBIGUOUS`() {
        every { client.findDeviceById(DEVICE_ID) } returns vsolDevice()
        every { subscriptionRepository.findByOnuSerialOrSuffix(any(), "86D819") } returns listOf(
            holder(1, "VSOL0086D819", ServiceStatus.ACTIVE),
            holder(2, "HWTC0086D819", ServiceStatus.CUT_OFF),
        )

        val result = service.link(DEVICE_ID)

        assertEquals(AcsLinkStatus.SKIP_AMBIGUOUS, result.status)
        verify(exactly = 0) { subscriptionRepository.save(any()) }
    }

    @Test
    fun `only CANCELLED holders are SKIP_NOT_ACTIVE`() {
        every { client.findDeviceById(DEVICE_ID) } returns vsolDevice()
        every { subscriptionRepository.findByOnuSerialOrSuffix(any(), "86D819") } returns listOf(
            holder(9, "VSOL0086D819", ServiceStatus.CANCELLED),
        )

        val result = service.link(DEVICE_ID)

        assertEquals(AcsLinkStatus.SKIP_NOT_ACTIVE, result.status)
        verify(exactly = 0) { subscriptionRepository.save(any()) }
    }

    @Test
    fun `missing device is SKIP_DEVICE_NOT_FOUND`() {
        every { client.findDeviceById(DEVICE_ID) } returns null

        val result = service.link(DEVICE_ID)

        assertEquals(AcsLinkStatus.SKIP_DEVICE_NOT_FOUND, result.status)
        verify(exactly = 0) { subscriptionRepository.findByOnuSerialOrSuffix(any(), any()) }
    }

    @Test
    fun `dry-run reports match without writing`() {
        every { client.findDeviceById(DEVICE_ID) } returns vsolDevice()
        every { subscriptionRepository.findByOnuSerialOrSuffix(any(), "86D819") } returns listOf(
            holder(2070, "VSOL0086D819", ServiceStatus.ACTIVE),
        )

        val result = service.link(DEVICE_ID, dryRun = true)

        assertEquals(AcsLinkStatus.LINKED, result.status)
        assertEquals(2070, result.subscriptionId)
        verify(exactly = 0) { subscriptionRepository.save(any()) }
        verify(exactly = 0) { syncService.upsertFromProvision(any(), any(), any()) }
        verify(exactly = 0) { tagger.apply(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { gateway.postJsonBody(any(), any()) }
    }

    @Test
    fun `replaces Core SN with Gateway inventory serial`() {
        every { client.findDeviceById(DEVICE_ID) } returns vsolDevice()
        val subscription = holder(629, "HWTC0086D819", ServiceStatus.ACTIVE)
        every { subscriptionRepository.findByOnuSerialOrSuffix(any(), "86D819") } returns listOf(subscription)

        service.link(DEVICE_ID)

        assertEquals("VSOL0086D819", subscription.fiberOnuSn)
    }

    @Test
    fun `ensure-mgmt 404 still links`() {
        every { client.findDeviceById(DEVICE_ID) } returns vsolDevice()
        every { subscriptionRepository.findByOnuSerialOrSuffix(any(), "86D819") } returns listOf(
            holder(2070, "VSOL0086D819", ServiceStatus.ACTIVE),
        )
        every { gateway.postJsonBody(any(), any()) } throws HttpClientErrorException(HttpStatus.NOT_FOUND)

        val result = service.link(DEVICE_ID)

        assertEquals(AcsLinkStatus.LINKED, result.status)
        verify { subscriptionRepository.save(any()) }
    }

    @Test
    fun `matches HWTC suffix against VSOL TR-069 serial`() {
        val hwtcId = "B46415-V2804AX15T-12345B46415F5F5A6"
        every { client.findDeviceById(hwtcId) } returns GenieAcsDevice(
            id = hwtcId,
            serialNumber = "12345B46415F5F5A6",
            lastInform = "2026-09-19T15:00:00.000Z",
        )
        val subscription = holder(1846, "HWTC15F5F5A6", ServiceStatus.ACTIVE)
        every { subscriptionRepository.findByOnuSerialOrSuffix(any(), "F5F5A6") } returns listOf(subscription)

        val result = service.link(hwtcId)

        assertEquals(AcsLinkStatus.LINKED, result.status)
        assertEquals(1846, result.subscriptionId)
    }

    @Test
    fun `listGhosts returns devices without occupied subscription`() {
        every { client.listDevices() } returns listOf(
            vsolDevice(),
            GenieAcsDevice(id = "ghost-31FAE6", serialNumber = "12345B4641531FAE6", lastInform = "2026-09-09T00:00:00Z"),
        )
        every { subscriptionRepository.findByOnuSerialOrSuffix(any(), "86D819") } returns listOf(
            holder(2070, "VSOL0086D819", ServiceStatus.ACTIVE),
        )
        every { subscriptionRepository.findByOnuSerialOrSuffix(any(), "31FAE6") } returns emptyList()

        val ghosts = service.listGhosts()

        assertEquals(1, ghosts.size)
        assertEquals("ghost-31FAE6", ghosts[0].deviceId)
        assertEquals("31FAE6", ghosts[0].suffix)
    }

    @Test
    fun `deleteGhost refuses a device with unique ACTIVE owner`() {
        every { client.findDeviceById(DEVICE_ID) } returns vsolDevice()
        every { subscriptionRepository.findByOnuSerialOrSuffix(any(), "86D819") } returns listOf(
            holder(2070, "VSOL0086D819", ServiceStatus.ACTIVE),
        )

        val ex = assertThrows(IllegalStateException::class.java) {
            service.deleteGhost(DEVICE_ID)
        }
        assertTrue(ex.message!!.contains("2070"))
        verify(exactly = 0) { client.deleteDevice(any()) }
    }

    @Test
    fun `deleteGhost removes NBI device without occupied owner`() {
        every { client.findDeviceById("ghost-31FAE6") } returns GenieAcsDevice(
            id = "ghost-31FAE6",
            serialNumber = "12345B4641531FAE6",
        )
        every { subscriptionRepository.findByOnuSerialOrSuffix(any(), "31FAE6") } returns emptyList()
        every { client.deleteDevice("ghost-31FAE6") } returns true

        assertTrue(service.deleteGhost("ghost-31FAE6"))
        verify { client.deleteDevice("ghost-31FAE6") }
    }

    private fun vsolDevice(lastInform: String = "2026-09-19T15:00:00.000Z") = GenieAcsDevice(
        id = DEVICE_ID,
        serialNumber = "12345B4641586D819",
        productClass = "V2804AX15T",
        lastInform = lastInform,
        ssid24 = "GIGA-24",
        ssid5 = "GIGA-5",
        connectionRequestUrl = "http://10.20.0.10:7547/",
    )

    private fun holder(id: Int, sn: String, status: ServiceStatus) = Subscription(
        id = id,
        firstName = "A",
        lastName = "B",
        fiberOnuSn = sn,
        equipmentCondition = EquipmentCondition.LOAN,
        serviceStatus = status,
        installationType = InstallationType.FIBER,
    )

    companion object {
        private const val DEVICE_ID = "B46415-V2804AX15T-12345B4641586D819"
    }
}
