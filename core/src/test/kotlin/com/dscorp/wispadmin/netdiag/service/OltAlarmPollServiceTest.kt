package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTarget
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagTargetRepository
import com.dscorp.wispadmin.netdiag.port.NetDiagOltCliPort
import com.dscorp.wispadmin.netdiag.port.NetDiagOltDescriptor
import com.dscorp.wispadmin.netdiag.port.NetDiagOltDescriptorPort
import com.dscorp.wispadmin.netdiag.port.OltCliOutcome
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.util.Optional

class OltAlarmPollServiceTest {

    private val cli = mockk<NetDiagOltCliPort>()
    private val ingestService = mockk<OltAlarmIngestService>()
    private val targetRepository = mockk<NetDiagTargetRepository>()
    private val descriptor = mutableDescriptor(alarmPollEnabled = true)
    private val service = OltAlarmPollService(
        cliPortProvider = availableProvider(cli),
        ingestService = ingestService,
        targetRepository = targetRepository,
        descriptorProvider = availableProvider(descriptor)
    )

    @Test
    fun `pollActiveAlarms ejecuta display alarm active all e ingesta`() {
        every { targetRepository.findByName("OLT-gigafiber-ma5608t") } returns Optional.of(
            NetDiagTarget(id = 42L, name = "OLT-gigafiber-ma5608t", deviceRefId = 1L)
        )
        every { cli.runAlarmPoll() } returns OltCliOutcome.Ok("ALARM DUMP")
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
        descriptor.alarmPollEnabled = false

        val result = service.pollActiveAlarms()

        assertEquals("alarm_poll_disabled", result.skippedReason)
        verify(exactly = 0) { cli.runAlarmPoll() }
    }

    @Test
    fun `poll se omite si no hay descriptor de oltgateway`() {
        val isolated = OltAlarmPollService(
            cliPortProvider = emptyProvider(),
            ingestService = ingestService,
            targetRepository = targetRepository,
            descriptorProvider = emptyProvider()
        )

        val result = isolated.pollActiveAlarms()

        assertEquals("olt_descriptor_unavailable", result.skippedReason)
    }

    private fun mutableDescriptor(alarmPollEnabled: Boolean) = object : NetDiagOltDescriptorPort {
        var alarmPollEnabled = alarmPollEnabled
        override fun descriptor() = NetDiagOltDescriptor(
            oltId = "gigafiber-ma5608t",
            host = "10.11.104.2",
            alarmPollEnabled = this.alarmPollEnabled,
            portsPerGponBoard = 16
        )
    }

    private fun <T : Any> availableProvider(value: T): ObjectProvider<T> {
        val provider = mockk<ObjectProvider<T>>()
        every { provider.ifAvailable } returns value
        return provider
    }

    private fun <T : Any> emptyProvider(): ObjectProvider<T> {
        val provider = mockk<ObjectProvider<T>>()
        every { provider.ifAvailable } returns null
        return provider
    }
}
