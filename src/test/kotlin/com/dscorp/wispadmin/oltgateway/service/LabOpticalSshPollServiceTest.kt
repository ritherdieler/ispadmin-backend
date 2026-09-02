package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.parser.OpticalInfoParser
import com.dscorp.wispadmin.oltgateway.ssh.CliBusResult
import com.dscorp.wispadmin.oltgateway.ssh.CliJobType
import com.dscorp.wispadmin.oltgateway.ssh.HuaweiCliSession
import com.dscorp.wispadmin.oltgateway.ssh.OltCliBus
import com.dscorp.wispadmin.servicehealth.port.HealthLabScopePort
import com.dscorp.wispadmin.servicehealth.port.HealthOnuPort
import com.dscorp.wispadmin.servicehealth.port.HealthOnuRef
import com.dscorp.wispadmin.wispadmin.config.GigafiberEnvironmentProperties
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.Onu
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional

class LabOpticalSshPollServiceTest {
    private val scope = mockk<HealthLabScopePort>()
    private val subscriptions = mockk<SubscriptionRepository>()
    private val onuPort = mockk<HealthOnuPort>()
    private val cliBus = mockk<OltCliBus>()
    private val publisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val environment = GigafiberEnvironmentProperties().apply { tag = "stg" }
    private val service = LabOpticalSshPollService(
        environment,
        scope,
        subscriptions,
        onuPort,
        cliBus,
        OpticalInfoParser(),
        publisher,
    )

    @Test
    fun `polls every lab onu over ssh and publishes one observation per olt`() {
        every { scope.collectionSubscriptionIds() } returns setOf(2329, 2330)
        every { subscriptions.findById(2329) } returns Optional.of(sub(2329, "12345B4641531C0B6"))
        every { subscriptions.findById(2330) } returns Optional.of(sub(2330, "ABCDEF123456"))
        every { onuPort.findBySn("12345B4641531C0B6") } returns HealthOnuRef(7627L, "VSOL0031C0B6", "a", 2L, "olt", 1, 6, 10)
        every { onuPort.findBySn("ABCDEF123456") } returns HealthOnuRef(99L, "VSOL00ABCDEF", "b", 2L, "olt", 1, 7, 3)
        val commands = mutableListOf<String>()
        every { cliBus.execute<Any>(CliJobType.ADHOC, any()) } answers {
            val block = secondArg<(HuaweiCliSession) -> Any>()
            val session = mockk<HuaweiCliSession>()
            every { session.execute(any()) } answers {
                val cmd = firstArg<String>()
                commands += cmd
                if (cmd.startsWith("display ont optical-info")) detailOutput() else "ok"
            }
            CliBusResult.Ok(block(session))
        }

        val result = service.pollAllLab()

        assertEquals(2, result.collected)
        assertEquals(0, result.unmapped)
        assertTrue(commands.contains("interface gpon 0/1"))
        assertTrue(commands.contains("display ont optical-info 6 10"))
        assertTrue(commands.contains("display ont optical-info 7 3"))
        val published = slot<OltOpticalObservation>()
        verify { publisher.publishEvent(capture(published)) }
        assertEquals(2L, published.captured.oltId)
        assertEquals(listOf(10, 3), published.captured.rows.map { it.optical.ontId })
    }

    @Test
    fun `prod environment tag skips the lab ssh poll`() {
        environment.tag = ""
        val skipped = service.pollAllLab()
        assertEquals("prod_environment", skipped.error)
        verify(exactly = 0) { cliBus.execute<Any>(any(), any()) }
    }

    @Test
    fun `refresh polls only the requested lab subscription`() {
        every { scope.collects(2329) } returns true
        every { subscriptions.findById(2329) } returns Optional.of(sub(2329, "12345B4641531C0B6"))
        every { onuPort.findBySn("12345B4641531C0B6") } returns HealthOnuRef(7627L, "VSOL0031C0B6", "a", 2L, "olt", 1, 6, 10)
        every { cliBus.execute<Any>(CliJobType.ADHOC, any()) } answers {
            val block = secondArg<(HuaweiCliSession) -> Any>()
            val session = mockk<HuaweiCliSession>()
            every { session.execute(any()) } answers {
                val cmd = firstArg<String>()
                if (cmd.startsWith("display ont optical-info")) detailOutput() else "ok"
            }
            CliBusResult.Ok(block(session))
        }

        val result = service.refreshSubscription(2329)

        assertTrue(result.collected)
        verify(exactly = 1) { cliBus.execute<Any>(CliJobType.ADHOC, any()) }
    }

    private fun sub(id: Int, sn: String) = Subscription(
        id = id,
        fiberOnuSn = sn,
        equipmentCondition = EquipmentCondition.values().first(),
    )

    private fun detailOutput() = """
        Rx optical power(dBm)          : -21.50
        Tx optical power(dBm)          : 2.10
        OLT Rx ONT optical power(dBm)  : -27.00
        Temperature(C)                 : 45
        Voltage(V)                     : 3.30
        Laser bias current(mA)         : 12
    """.trimIndent()
}
