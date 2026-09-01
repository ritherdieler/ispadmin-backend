package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.observability.ObservabilityReporter
import com.dscorp.wispadmin.wispadmin.observability.ReportedEvent
import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.IpPool
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.IpPoolRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.mikrotik.IMikroTikService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class IpAllocationServiceTest {

    private val ipPoolRepository = mockk<IpPoolRepository>()
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val mikrotikService = mockk<IMikroTikService>()
    private val observabilityReporter = mockk<ObservabilityReporter>(relaxed = true)
    private lateinit var service: IpAllocationService

    private val host = NetworkDevice(id = 8, name = "MK2")
    private val pool = IpPool(
        id = 22,
        ipSegment = "192.168.30.0/24",
        isEligible = true,
        hostDevice = host
    )

    @BeforeEach
    fun setUp() {
        service = IpAllocationService(
            ipPoolRepository = ipPoolRepository,
            subscriptionRepository = subscriptionRepository,
            mikrotikService = mikrotikService,
            observabilityReporter = observabilityReporter
        )
        every { ipPoolRepository.findAllEligiblePools() } returns listOf(pool)
        every { subscriptionRepository.findActiveIps() } returns emptyList()
        every { subscriptionRepository.findByIpAndServiceStatus(any(), any()) } returns emptyList()
        every { subscriptionRepository.existsByIpAndServiceStatus(any(), any()) } returns false
        stubQueues(emptyList())
    }

    @Test
    fun `allocate returns first octet when pool has no ACTIVE ips and no queues`() {
        val (ip, assignedPool) = service.allocate(hostDeviceId = 8)

        assertEquals("192.168.30.10", ip)
        assertEquals(22, assignedPool.id)
        verify(exactly = 0) { observabilityReporter.report(any()) }
    }

    @Test
    fun `allocate prefers last plus one from ACTIVE ips`() {
        every { subscriptionRepository.findActiveIps() } returns ((10..50) + (52..190)).map { "192.168.30.$it" }

        val (ip, _) = service.allocate(hostDeviceId = 8)

        assertEquals("192.168.30.191", ip)
    }

    @Test
    fun `allocate falls back to lowest hole when last octet is 250`() {
        every { subscriptionRepository.findActiveIps() } returns ((10..50) + (52..250)).map { "192.168.30.$it" }

        val (ip, _) = service.allocate(hostDeviceId = 8)

        assertEquals("192.168.30.51", ip)
    }

    @Test
    fun `allocate reports active collision and returns next free ip`() {
        every { subscriptionRepository.existsByIpAndServiceStatus("192.168.30.10", ServiceStatus.ACTIVE) } returns true
        every {
            subscriptionRepository.findByIpAndServiceStatus("192.168.30.10", ServiceStatus.ACTIVE)
        } returns listOf(activeSubscription(id = 629, ip = "192.168.30.10"))
        val captured = slot<ReportedEvent>()
        every { observabilityReporter.report(capture(captured)) } returns Unit

        val (ip, _) = service.allocate(hostDeviceId = 8)

        assertEquals("192.168.30.11", ip)
        assertEquals("ip_collision", captured.captured.eventType)
        assertEquals("backend", captured.captured.platform)
        assertEquals("warning", captured.captured.severity)
        assertEquals("IpCollision", captured.captured.errorType)
        assertEquals("192.168.30.10", captured.captured.tags?.get("ip"))
        assertEquals("active_subscription", captured.captured.tags?.get("reason"))
        assertEquals("auto", captured.captured.tags?.get("source"))
        assertEquals(8, captured.captured.tags?.get("hostDeviceId"))
        assertEquals(629, captured.captured.tags?.get("conflictingSubscriptionId"))
    }

    @Test
    fun `allocate reports mikrotik queue collision and returns next free ip`() {
        stubQueues(
            listOf(
                mapOf(
                    "name" to "id:744, usuario:Judith Quispe, lugar:Huacho",
                    "target" to "192.168.30.10/32"
                )
            )
        )
        val captured = slot<ReportedEvent>()
        every { observabilityReporter.report(capture(captured)) } returns Unit

        val (ip, _) = service.allocate(hostDeviceId = 8)

        assertEquals("192.168.30.11", ip)
        assertEquals("ip_collision", captured.captured.eventType)
        assertEquals("mikrotik_queue", captured.captured.tags?.get("reason"))
        assertEquals("192.168.30.10", captured.captured.tags?.get("ip"))
        assertEquals("auto", captured.captured.tags?.get("source"))
        assertEquals(744, captured.captured.tags?.get("conflictingSubscriptionId"))
        assertTrue(captured.captured.tags?.get("queueName").toString().startsWith("id:744"))
    }

    @Test
    fun `allocate uses preferred ip when it is free`() {
        val (ip, _) = service.allocate(hostDeviceId = 8, preferredIp = "192.168.30.77")

        assertEquals("192.168.30.77", ip)
        verify(exactly = 0) { observabilityReporter.report(any()) }
    }

    @Test
    fun `allocate does not force preferred ip when it collides and assigns another`() {
        every { subscriptionRepository.findActiveIps() } returns listOf("192.168.30.77")
        every {
            subscriptionRepository.findByIpAndServiceStatus("192.168.30.77", ServiceStatus.ACTIVE)
        } returns listOf(activeSubscription(id = 100, ip = "192.168.30.77"))

        val (ip, _) = service.allocate(hostDeviceId = 8, preferredIp = "192.168.30.77")

        assertEquals("192.168.30.78", ip)
        verify(exactly = 1) {
            observabilityReporter.report(match { event ->
                event.eventType == "ip_collision" &&
                    event.tags?.get("ip") == "192.168.30.77" &&
                    event.tags?.get("reason") == "active_subscription"
            })
        }
    }

    @Test
    fun `allocate continues when observability reporter fails`() {
        every { subscriptionRepository.existsByIpAndServiceStatus("192.168.30.10", ServiceStatus.ACTIVE) } returns true
        every { observabilityReporter.report(any()) } throws RuntimeException("obs down")

        val (ip, _) = service.allocate(hostDeviceId = 8)

        assertEquals("192.168.30.11", ip)
    }

    @Test
    fun `allocate throws when pool is exhausted`() {
        every { subscriptionRepository.findActiveIps() } returns (10..250).map { "192.168.30.$it" }

        val error = assertThrows(IllegalStateException::class.java) {
            service.allocate(hostDeviceId = 8)
        }

        assertTrue(error.message!!.contains("No more ips available"))
    }

    private fun stubQueues(rows: List<Map<String, String>>) {
        every { mikrotikService.executeOnDevice(any(), any()) } answers {
            val block = secondArg<(MikrotikSession) -> Unit>()
            val session = mockk<MikrotikSession>()
            every { session.print("/queue/simple", any()) } returns rows
            block(session)
        }
    }

    private fun activeSubscription(id: Int, ip: String) = Subscription(
        id = id,
        firstName = "Cliente",
        lastName = "Activo",
        dni = id.toString().padStart(8, '0'),
        equipmentCondition = EquipmentCondition.LOAN,
        serviceStatus = ServiceStatus.ACTIVE
    ).apply { this.ip = ip }
}
