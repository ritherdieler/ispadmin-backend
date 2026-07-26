package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagProbeRun
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTarget
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagProbeRunRepository
import com.dscorp.wispadmin.netdiag.port.NetDiagDeviceDirectoryPort
import com.dscorp.wispadmin.routeros.port.MikrotikAuthException
import com.dscorp.wispadmin.routeros.port.MikrotikClient
import com.dscorp.wispadmin.routeros.port.MikrotikDeviceRef
import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.dscorp.wispadmin.routeros.port.MikrotikUnreachableException
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional

class MikrotikPollAdapterTest {

    private val mikrotikClient = mockk<MikrotikClient>()
    private val deviceDirectory = mockk<NetDiagDeviceDirectoryPort>()
    private val probeRunRepository = mockk<NetDiagProbeRunRepository>()
    private val objectMapper = ObjectMapper()
    private val adapter = MikrotikPollAdapter(
        mikrotikClient = mikrotikClient,
        deviceDirectory = deviceDirectory,
        probeRunRepository = probeRunRepository,
        objectMapper = objectMapper
    )

    private val target = NetDiagTarget(
        id = 1L,
        name = "MK1",
        deviceRefId = 7L,
        monitorConfig = """{"criticalInterfaces":["ether1","sfp-sfpplus1"],"expectedFirmware":"7.23.2"}"""
    )

    private val deviceRef = MikrotikDeviceRef(
        id = "7",
        host = "38.224.231.2",
        port = 443,
        username = "admin",
        password = "secret"
    )

    @BeforeEach
    fun stubPreviousProbe() {
        every {
            probeRunRepository.findTopByTargetIdAndStatusOrderByStartedAtDesc(1L, "SUCCESS")
        } returns Optional.empty()
    }

    @Test
    fun `poll exitoso persiste probe_run SUCCESS con payload de interfaces health routerboard resource`() {
        every { deviceDirectory.findMikrotikDeviceRef(7L) } returns deviceRef
        val session = mockk<MikrotikSession>()
        every {
            mikrotikClient.withSession(deviceRef, any<(MikrotikSession) -> PollSnapshot>())
        } answers {
            val block = arg<(MikrotikSession) -> PollSnapshot>(1)
            block(session)
        }
        every { session.print("/interface") } returns listOf(
            mapOf("name" to "ether1", "type" to "ether", "running" to "true", "disabled" to "false"),
            mapOf("name" to "gre-tunnel1", "type" to "gre-tunnel", "running" to "true", "disabled" to "false")
        )
        every { session.print("/system/health") } returns listOf(
            mapOf("name" to "voltage", "value" to "24.1"),
            mapOf("name" to "psu1-state", "value" to "ok")
        )
        every { session.print("/system/routerboard") } returns listOf(
            mapOf("current-firmware" to "7.23.2", "upgrade-firmware" to "7.23.2")
        )
        every { session.print("/system/resource") } returns listOf(
            mapOf("uptime" to "1d2h3m4s", "cpu-load" to "12", "version" to "7.23.2 (stable)")
        )
        val saved = slot<NetDiagProbeRun>()
        every { probeRunRepository.save(capture(saved)) } answers { firstArg() }

        val result = adapter.poll(target)

        assertEquals("SUCCESS", result.status)
        assertNotNull(result.payload)
        assertTrue(result.payload!!.contains("ether1"))
        assertTrue(result.payload!!.contains("gre-tunnel1"))
        assertTrue(result.payload!!.contains("voltage"))
        assertEquals("SUCCESS", saved.captured.status)
        assertNotNull(saved.captured.finishedAt)
        assertNotNull(saved.captured.latencyMs)
    }

    @Test
    fun `poll inalcanzable persiste probe_run FAILED con reason DEVICE_UNREACHABLE`() {
        every { deviceDirectory.findMikrotikDeviceRef(7L) } returns deviceRef
        every {
            mikrotikClient.withSession(deviceRef, any<(MikrotikSession) -> PollSnapshot>())
        } throws MikrotikUnreachableException("down")
        val saved = slot<NetDiagProbeRun>()
        every { probeRunRepository.save(capture(saved)) } answers { firstArg() }

        val result = adapter.poll(target)

        assertEquals("FAILED", result.status)
        assertEquals("DEVICE_UNREACHABLE", result.errorReasonCode)
        assertTrue(saved.captured.error!!.contains("down"))
    }

    @Test
    fun `poll sin credenciales en directory persiste FAILED DEVICE_NOT_FOUND`() {
        every { deviceDirectory.findMikrotikDeviceRef(7L) } returns null
        val saved = slot<NetDiagProbeRun>()
        every { probeRunRepository.save(capture(saved)) } answers { firstArg() }

        val result = adapter.poll(target)

        assertEquals("FAILED", result.status)
        assertEquals("DEVICE_NOT_FOUND", result.errorReasonCode)
        verify(exactly = 0) {
            mikrotikClient.withSession(any(), any<(MikrotikSession) -> PollSnapshot>())
        }
    }

    @Test
    fun `poll auth failure mapea AUTH_FAILURE`() {
        every { deviceDirectory.findMikrotikDeviceRef(7L) } returns deviceRef
        every {
            mikrotikClient.withSession(deviceRef, any<(MikrotikSession) -> PollSnapshot>())
        } throws MikrotikAuthException("bad creds")
        every { probeRunRepository.save(any()) } answers { firstArg() }

        val result = adapter.poll(target)

        assertEquals("FAILED", result.status)
        assertEquals("AUTH_FAILURE", result.errorReasonCode)
    }
}
