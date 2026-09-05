package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.parser.HuaweiOltAlarmParser
import com.dscorp.wispadmin.oltgateway.ssh.CliBusResult
import com.dscorp.wispadmin.oltgateway.ssh.CliJobType
import com.dscorp.wispadmin.oltgateway.ssh.HuaweiCliSession
import com.dscorp.wispadmin.oltgateway.ssh.OltCliBus
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider

class OltAlarmCliServiceTest {

    @Test
    fun `descriptor expone subset de OltGatewayProperties`() {
        val properties = OltGatewayProperties().apply {
            oltId = "gigafiber-ma5608t"
            host = "10.11.104.2"
            sync.alarmEnabled = false
            inventory.defaultPortsPerGponBoard = 8
        }

        val descriptor = OltAlarmCliService(mockk(relaxed = true), HuaweiOltAlarmParser(), properties).descriptor()

        assertEquals("gigafiber-ma5608t", descriptor.oltId)
        assertEquals("10.11.104.2", descriptor.host)
        assertFalse(descriptor.alarmPollEnabled)
        assertEquals(8, descriptor.portsPerGponBoard)
    }

    @Test
    fun `parser mapea alarmas parseadas y marca unparsed`() {
        val service = OltAlarmCliService(mockk(relaxed = true), HuaweiOltAlarmParser(), OltGatewayProperties())
        val alarms = service.parse("texto que no es alarma")

        assertEquals(1, alarms.size)
        assertTrue(alarms[0].unparsed)
        assertEquals(HuaweiOltAlarmParser.REASON_UNPARSED, alarms[0].reasonCode)
    }

    @Test
    fun `poll ejecuta display alarm active all`() {
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

        val result = OltAlarmCliService(provider, HuaweiOltAlarmParser(), OltGatewayProperties()).pollActiveAlarms()

        assertEquals("ALARM DUMP", result.raw)
    }

    @Test
    fun `poll se salta si no hay bus`() {
        val provider = mockk<ObjectProvider<OltCliBus>>()
        every { provider.ifAvailable } returns null

        val result = OltAlarmCliService(provider, HuaweiOltAlarmParser(), OltGatewayProperties()).pollActiveAlarms()

        assertEquals("cli_bus_unavailable", result.skippedReason)
    }
}
