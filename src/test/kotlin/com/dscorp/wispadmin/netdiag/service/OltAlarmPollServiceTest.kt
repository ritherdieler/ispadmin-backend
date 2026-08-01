package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTarget
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagTargetRepository
import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.ssh.CliBusResult
import com.dscorp.wispadmin.oltgateway.ssh.CliJobType
import com.dscorp.wispadmin.oltgateway.ssh.HuaweiCliSession
import com.dscorp.wispadmin.oltgateway.ssh.OltCliBus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.util.Optional

class OltAlarmPollServiceTest {

    private val cliBus = mockk<OltCliBus>()
    private val cliBusProvider = mockk<ObjectProvider<OltCliBus>>()
    private val ingestService = mockk<OltAlarmIngestService>()
    private val targetRepository = mockk<NetDiagTargetRepository>()
    private val properties = OltGatewayProperties().apply {
        oltId = "gigafiber-ma5608t"
        host = "10.11.104.2"
        sync.alarmEnabled = true
    }
    private val service = OltAlarmPollService(
        cliBusProvider = cliBusProvider,
        ingestService = ingestService,
        targetRepository = targetRepository,
        oltGatewayProperties = properties
    )

    @Test
    fun `pollActiveAlarms ejecuta display alarm active all e ingesta`() {
        every { cliBusProvider.ifAvailable } returns cliBus
        every { targetRepository.findByName("OLT-gigafiber-ma5608t") } returns Optional.of(
            NetDiagTarget(id = 42L, name = "OLT-gigafiber-ma5608t", deviceRefId = 1L)
        )
        every { cliBus.execute(CliJobType.ALARM_POLL, any<(HuaweiCliSession) -> String>()) } answers {
            val block = secondArg<(HuaweiCliSession) -> String>()
            val session = mockk<HuaweiCliSession>()
            every { session.execute(any<String>()) } answers {
                val cmd = firstArg<String>()
                if (cmd.startsWith("display alarm")) "ALARM DUMP" else "ok"
            }
            every { session.execute(any<String>(), any<Long>()) } answers {
                val cmd = firstArg<String>()
                if (cmd.startsWith("display alarm")) "ALARM DUMP" else "ok"
            }
            CliBusResult.Ok(block(session))
        }
        every {
            ingestService.ingestCliActiveAlarms("ALARM DUMP", "10.11.104.2", 42L, "cli_alarm_active")
        } returns OltAlarmIngestResult(
            persisted = 3,
            parsed = 2,
            unparsed = 1,
            alertsEmitted = 2,
            cleared = 1,
            openedIncidentIds = listOf(9L)
        )

        val result = service.pollActiveAlarms()

        assertNull(result.skippedReason)
        assertNull(result.error)
        assertEquals(3, result.alarmsPersisted)
        assertEquals(2, result.alertsEmitted)
        assertEquals(1, result.cleared)
        assertEquals(listOf(9L), result.openedIncidentIds)
        verify { ingestService.ingestCliActiveAlarms("ALARM DUMP", "10.11.104.2", 42L, "cli_alarm_active") }
    }

    @Test
    fun `poll se omite si alarm sync disabled`() {
        properties.sync.alarmEnabled = false
        every { cliBusProvider.ifAvailable } returns cliBus

        val result = service.pollActiveAlarms()

        assertEquals("alarm_poll_disabled", result.skippedReason)
        verify(exactly = 0) { cliBus.execute(any(), any<(HuaweiCliSession) -> String>()) }
    }
}
