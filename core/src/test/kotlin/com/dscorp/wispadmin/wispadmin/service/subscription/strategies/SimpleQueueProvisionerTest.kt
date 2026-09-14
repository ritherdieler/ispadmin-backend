package com.dscorp.wispadmin.wispadmin.service.subscription.strategies

import com.dscorp.wispadmin.routeros.port.MikrotikCommandException
import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.extensions.executeCommand
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.mikrotik.IQueueManager
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SimpleQueueProvisionerTest {

    private val queueManager = mockk<IQueueManager>(relaxed = true)
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private lateinit var provisioner: SimpleQueueProvisioner
    private lateinit var session: MikrotikSession

    private val device = NetworkDevice(id = 8, name = "MK2")
    private val plan = Plan(id = 54, name = "f50", downloadSpeed = 50, uploadSpeed = 50)
    private val subscription = Subscription(
        id = 2291,
        firstName = "Sergio",
        lastName = "Carrillo",
        equipmentCondition = EquipmentCondition.LOAN
    ).apply { ip = "192.168.30.213" }

    @BeforeEach
    fun setUp() {
        provisioner = SimpleQueueProvisioner(queueManager, subscriptionRepository)
        session = mockk(relaxed = true)
        mockkStatic("com.dscorp.wispadmin.wispadmin.extensions.ExtensionsKt")
        every { any<NetworkDevice>().executeCommand(any()) } answers {
            val block = secondArg<(MikrotikSession) -> Unit>()
            block(session)
        }
        every { queueManager.buildQueueName(any()) } returns "id:2291, usuario:Sergio Carrillo"
        every { subscriptionRepository.findByIpAndServiceStatus(any(), ServiceStatus.ACTIVE) } returns emptyList()
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("com.dscorp.wispadmin.wispadmin.extensions.ExtensionsKt")
    }

    @Test
    fun `creates queue when target is free`() {
        every { session.print("/queue/simple", mapOf("target" to "192.168.30.213/32")) } returns emptyList()

        val result = provisioner.ensureQueue(subscription, device, plan)

        assertTrue(result.added)
        assertNull(result.error)
        verify(exactly = 1) {
            session.add(
                "/queue/simple",
                mapOf(
                    "name" to "id:2291, usuario:Sergio Carrillo",
                    "target" to "192.168.30.213",
                    "max-limit" to "50M/50M"
                )
            )
        }
    }

    @Test
    fun `treats own queue as idempotent success`() {
        every { session.print("/queue/simple", mapOf("target" to "192.168.30.213/32")) } returns listOf(
            mapOf("name" to "id:2291, usuario:Sergio Carrillo", "target" to "192.168.30.213/32")
        )

        val result = provisioner.ensureQueue(subscription, device, plan)

        assertTrue(result.added)
        verify(exactly = 0) { session.add(any(), any()) }
        verify(exactly = 0) { queueManager.recreateQueueForSubscription(any(), any()) }
    }

    @Test
    fun `fails when queue belongs to another subscription`() {
        every { session.print("/queue/simple", mapOf("target" to "192.168.30.213/32")) } returns listOf(
            mapOf("name" to "id:744, usuario:Judith Quispe", "target" to "192.168.30.213/32")
        )

        val result = provisioner.ensureQueue(subscription, device, plan)

        assertFalse(result.added)
        assertTrue(result.error!!.contains("192.168.30.213"))
        assertTrue(result.error!!.contains("744"))
        verify(exactly = 0) { session.add(any(), any()) }
        verify(exactly = 0) { queueManager.recreateQueueForSubscription(any(), any()) }
    }

    @Test
    fun `reclaims orphan legacy queue when no other ACTIVE owns the ip`() {
        every { session.print("/queue/simple", mapOf("target" to "192.168.30.213/32")) } returns listOf(
            mapOf("name" to "Judith Quispe - f50", "target" to "192.168.30.213/32")
        )
        every { queueManager.recreateQueueForSubscription(session, subscription) } returns true

        val result = provisioner.ensureQueue(subscription, device, plan)

        assertTrue(result.added)
        verify(exactly = 1) { queueManager.recreateQueueForSubscription(session, subscription) }
        verify(exactly = 0) { session.add(any(), any()) }
    }

    @Test
    fun `fails orphan reclaim when another ACTIVE subscription owns the ip`() {
        every { session.print("/queue/simple", mapOf("target" to "192.168.30.213/32")) } returns listOf(
            mapOf("name" to "Judith Quispe - f50", "target" to "192.168.30.213/32")
        )
        every {
            subscriptionRepository.findByIpAndServiceStatus("192.168.30.213", ServiceStatus.ACTIVE)
        } returns listOf(
            Subscription(id = 744, firstName = "Judith", equipmentCondition = EquipmentCondition.LOAN)
                .apply { ip = "192.168.30.213" }
        )

        val result = provisioner.ensureQueue(subscription, device, plan)

        assertFalse(result.added)
        assertTrue(result.error!!.contains("ACTIVE"))
        verify(exactly = 0) { queueManager.recreateQueueForSubscription(any(), any()) }
    }

    @Test
    fun `does not reclaim a queue that belongs to another environment`() {
        every { session.print("/queue/simple", mapOf("target" to "192.168.30.213/32")) } returns listOf(
            mapOf("name" to "[stg] id:2291, usuario:Sergio Carrillo", "target" to "192.168.30.213/32")
        )

        val result = provisioner.ensureQueue(subscription, device, plan)

        assertFalse(result.added)
        assertTrue(result.error!!.contains("stg"))
        verify(exactly = 0) { session.add(any(), any()) }
        verify(exactly = 0) { queueManager.recreateQueueForSubscription(any(), any()) }
    }

    @Test
    fun `treats own tagged queue as idempotent success`() {
        provisioner = SimpleQueueProvisioner(queueManager, subscriptionRepository, "stg")
        every { session.print("/queue/simple", mapOf("target" to "192.168.30.213/32")) } returns listOf(
            mapOf("name" to "[stg] id:2291, usuario:Sergio Carrillo", "target" to "192.168.30.213/32")
        )

        val result = provisioner.ensureQueue(subscription, device, plan)

        assertTrue(result.added)
        verify(exactly = 0) { session.add(any(), any()) }
        verify(exactly = 0) { queueManager.recreateQueueForSubscription(any(), any()) }
    }

    @Test
    fun `does not throw when MikroTik handshake fails`() {
        every { any<NetworkDevice>().executeCommand(any()) } throws MikrotikCommandException(
            "rest PUT /rest/queue/simple: Remote host terminated the handshake"
        )

        val result = provisioner.ensureQueue(subscription, device, plan)

        assertFalse(result.added)
        assertEquals(
            "rest PUT /rest/queue/simple: Remote host terminated the handshake",
            result.error
        )
    }
}
