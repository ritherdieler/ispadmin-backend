package com.dscorp.wispadmin.wispadmin.service.mikrotik

import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.dscorp.wispadmin.wispadmin.config.GigafiberEnvironmentProperties
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.NapBox
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Place
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.extensions.executeCommand
import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.ScheduledTaskLogService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class QueueManagerServiceTest {

    private val subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true)
    private val mikrotikService = mockk<IMikroTikService>(relaxed = true)
    private val errorLogRepository = mockk<ErrorLogRepository>(relaxed = true)
    private val scheduledTaskLogService = mockk<ScheduledTaskLogService>(relaxed = true)
    private lateinit var session: MikrotikSession

    private val device = NetworkDevice(id = 8, name = "MK2")
    private val subscription = Subscription(
        id = 2291,
        firstName = "Sergio",
        lastName = "Carrillo",
        equipmentCondition = EquipmentCondition.LOAN
    ).apply {
        ip = "192.168.30.213"
        installationType = InstallationType.FIBER
        place = Place(id = 1, name = "Huacho")
        napBox = NapBox(id = 1, code = "NAP-01")
        plan = Plan(id = 54, name = "f50", downloadSpeed = 50, uploadSpeed = 20, type = InstallationType.FIBER)
        hostDevice = device
    }

    @BeforeEach
    fun setUp() {
        session = mockk(relaxed = true)
        mockkStatic("com.dscorp.wispadmin.wispadmin.extensions.ExtensionsKt")
        every { any<NetworkDevice>().executeCommand(any()) } answers {
            val block = secondArg<(MikrotikSession) -> Unit>()
            block(session)
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("com.dscorp.wispadmin.wispadmin.extensions.ExtensionsKt")
    }

    private fun service(tag: String): QueueManagerService {
        val environment = GigafiberEnvironmentProperties().apply { this.tag = tag }
        return QueueManagerService(
            subscriptionRepository,
            mikrotikService,
            errorLogRepository,
            scheduledTaskLogService,
            environment
        )
    }

    @Test
    fun `prod queue name has no env prefix`() {
        val name = service("").buildQueueName(subscription)
        assertTrue(name.startsWith("id:2291"))
        assertFalse(name.startsWith("[stg]"))
    }

    @Test
    fun `staging queue name is prefixed with env tag`() {
        val name = service("stg").buildQueueName(subscription)
        assertTrue(name.startsWith("[stg] id:2291"))
        assertTrue(name.contains("usuario:Sergio Carrillo"))
        assertTrue(name.contains("nap:NAP-01"))
    }

    @Test
    fun `staging add writes env comment`() {
        every { session.print("/queue/simple", any()) } returns emptyList()

        service("stg").configureMikroTikQueue(session, subscription)

        verify {
            session.add(
                "/queue/simple",
                match {
                    it["name"]?.startsWith("[stg] id:2291") == true &&
                        it["target"] == "192.168.30.213" &&
                        it["max-limit"] == "20M/50M" &&
                        it["comment"] == "env=stg"
                }
            )
        }
    }

    @Test
    fun `update removes only queues of the same environment`() {
        every { session.print("/queue/simple", mapOf("target" to "192.168.30.213/32")) } returns listOf(
            mapOf(".id" to "*1", "name" to "id:744, usuario:Judith"),
            mapOf(".id" to "*2", "name" to "[stg] id:2291, usuario:Sergio Carrillo")
        )

        service("stg").updateMikroTikQueue(subscription)

        verify(exactly = 1) { session.remove("/queue/simple", "*2") }
        verify(exactly = 0) { session.remove("/queue/simple", "*1") }
        verify {
            session.add(
                "/queue/simple",
                match { it["comment"] == "env=stg" && it["name"]?.startsWith("[stg]") == true }
            )
        }
    }

    @Test
    fun `recreate does not delete a queue from another environment`() {
        every { session.print("/queue/simple", mapOf("target" to "192.168.30.213/32")) } returns listOf(
            mapOf(".id" to "*1", "name" to "id:744, usuario:Judith")
        )

        val added = service("stg").recreateQueueForSubscription(session, subscription)

        assertTrue(added)
        verify(exactly = 0) { session.remove(any(), any()) }
        verify {
            session.add(
                "/queue/simple",
                match { it["comment"]?.contains("env=stg") == true && it["comment"]?.contains("FIBER") == true }
            )
        }
    }
}
