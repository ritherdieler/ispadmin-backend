package com.dscorp.wispadmin.oltgateway.adapter

import com.dscorp.wispadmin.netdiag.port.OltCliOutcome
import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOlt
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuStatusCurrent
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.dscorp.wispadmin.oltgateway.parser.HuaweiOltAlarmParser
import com.dscorp.wispadmin.oltgateway.ssh.CliBusResult
import com.dscorp.wispadmin.oltgateway.ssh.CliJobType
import com.dscorp.wispadmin.oltgateway.ssh.HuaweiCliSession
import com.dscorp.wispadmin.oltgateway.ssh.OltCliBus
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.math.BigDecimal
import java.util.Optional

class NetDiagOltAdaptersTest {

    @Test
    fun `descriptor expone subset de OltGatewayProperties`() {
        val properties = OltGatewayProperties().apply {
            oltId = "gigafiber-ma5608t"
            host = "10.11.104.2"
            sync.alarmEnabled = false
            inventory.defaultPortsPerGponBoard = 8
        }

        val descriptor = NetDiagOltDescriptorAdapter(properties).descriptor()

        assertEquals("gigafiber-ma5608t", descriptor.oltId)
        assertEquals("10.11.104.2", descriptor.host)
        assertFalse(descriptor.alarmPollEnabled)
        assertEquals(8, descriptor.portsPerGponBoard)
    }

    @Test
    fun `parser mapea alarmas parseadas y marca unparsed`() {
        val adapter = NetDiagOltAlarmParserAdapter(HuaweiOltAlarmParser())
        val alarms = adapter.parseActiveAlarms("texto que no es alarma")

        assertEquals(1, alarms.size)
        assertTrue(alarms[0].unparsed)
        assertEquals(HuaweiOltAlarmParser.REASON_UNPARSED, alarms[0].reasonCode)
    }

    @Test
    fun `cli adapter ejecuta display alarm active all`() {
        val bus = mockk<OltCliBus>()
        val provider = mockk<ObjectProvider<OltCliBus>>()
        every { provider.ifAvailable } returns bus
        every { bus.execute(CliJobType.ALARM_POLL, any<(HuaweiCliSession) -> String>()) } answers {
            val block = secondArg<(HuaweiCliSession) -> String>()
            val session = mockk<HuaweiCliSession>()
            every { session.execute(any<String>()) } returns "ok"
            every { session.execute(any<String>(), any<Long>()) } answers {
                val cmd = firstArg<String>()
                if (cmd.startsWith("display alarm")) "ALARM DUMP" else "ok"
            }
            CliBusResult.Ok(block(session))
        }

        val outcome = NetDiagOltCliAdapter(provider).runAlarmPoll()

        assertEquals(OltCliOutcome.Ok("ALARM DUMP"), outcome)
    }

    @Test
    fun `cli adapter se salta si no hay bus`() {
        val provider = mockk<ObjectProvider<OltCliBus>>()
        every { provider.ifAvailable } returns null

        val outcome = NetDiagOltCliAdapter(provider).runAlarmPoll()

        assertEquals(OltCliOutcome.Skipped("cli_bus_unavailable"), outcome)
    }

    @Test
    fun `inventory adapter lista ONUs del PON`() {
        val oltRepository = mockk<OltMgrOltRepository>()
        val onuRepository = mockk<OltMgrOnuRepository>()
        val olt = OltMgrOlt(id = 1L, name = "gigafiber-ma5608t", ipAddress = "10.11.104.2")
        val onu = OltMgrOnu(id = 16L, sn = "HWTC1", olt = olt, board = 0, port = 1, onuIndex = 16)
        onu.status = OltMgrOnuStatusCurrent(
            onuId = 16L,
            onu = onu,
            runState = "offline",
            lastDownCause = "dying-gasp",
            onuRxDbm = BigDecimal("-21.10")
        )
        every { oltRepository.findByName("gigafiber-ma5608t") } returns Optional.of(olt)
        every { oltRepository.findByName("missing") } returns Optional.empty()
        every { onuRepository.findByOlt_IdAndBoardAndPortWithStatus(1L, 0, 1) } returns listOf(onu)
        every {
            onuRepository.findByOlt_IdAndBoardAndPortAndOnuIndexAndDeletedAtIsNull(1L, 0, 1, 16)
        } returns Optional.of(onu)

        val adapter = NetDiagOltInventoryAdapter(oltRepository, onuRepository)

        assertEquals(1L, adapter.findOltId("gigafiber-ma5608t"))
        val listed = adapter.listOnusOnPon("gigafiber-ma5608t", 0, 1)
        assertEquals(1, listed.size)
        assertEquals(16, listed[0].onuIndex)
        assertEquals("HWTC1", listed[0].sn)
        assertEquals("offline", listed[0].runState)
        assertEquals("dying-gasp", listed[0].lastDownCause)
        assertEquals(-21.10, listed[0].onuRxDbm)
        assertEquals("HWTC1", adapter.findOnu("gigafiber-ma5608t", 0, 1, 16)?.sn)
        assertNull(adapter.findOltId("missing"))
    }
}
