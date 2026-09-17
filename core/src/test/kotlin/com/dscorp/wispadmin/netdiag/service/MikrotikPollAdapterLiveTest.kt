package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagProbeRun
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTarget
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagProbeRunRepository
import com.dscorp.wispadmin.netdiag.port.NetDiagDeviceDirectoryPort
import com.dscorp.wispadmin.routeros.Mk1LiveSupport
import com.dscorp.wispadmin.routeros.adapter.RouterOs7RestAdapter
import com.dscorp.wispadmin.routeros.port.MikrotikCommandException
import com.dscorp.wispadmin.routeros.port.MikrotikDeviceRef
import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.dscorp.wispadmin.routeros.port.RouterOsSessionFactory
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.Optional

@Tag("live-mk1")
class MikrotikPollAdapterLiveTest {

    private val probeRunRepository = mockk<NetDiagProbeRunRepository>()
    private val deviceDirectory = mockk<NetDiagDeviceDirectoryPort>()
    private val objectMapper = ObjectMapper()
    private val liveClient = RouterOs7RestAdapter(Mk1LiveSupport.liveRestProperties(timeoutMs = 30000))
    private val adapter = MikrotikPollAdapter(
        sessionFactory = RouterOsSessionFactory { hostDeviceId ->
            val device = deviceDirectory.findMikrotikDeviceRef(hostDeviceId.toLong())
                ?: error("device ref missing")
            LiveDelegatingSession(liveClient, device)
        },
        deviceDirectory = deviceDirectory,
        probeRunRepository = probeRunRepository,
        objectMapper = objectMapper,
        netwatchAdapter = MikrotikNetwatchAdapter(),
        opticalAdapter = MikrotikOpticalAdapter()
    )

    private val target = NetDiagTarget(
        id = 1L,
        name = "MK1",
        deviceRefId = 1L,
        monitorConfig = """{"criticalInterfaces":["sfp-sfpplus1","gre-ispadmin-vps"],"expectedFirmware":"7.23.2","netwatchNames":[],"opticalInterfaces":["sfp-sfpplus1"]}"""
    )

    @BeforeEach
    fun stubRepository() {
        every {
            probeRunRepository.findTopByTargetIdAndStatusOrderByStartedAtDesc(1L, "SUCCESS")
        } returns Optional.empty()
        every { probeRunRepository.save(any()) } answers { firstArg() }
    }

    @Test
    fun `poll MK1 via REST persists SUCCESS probe with interface snapshot`() {
        Mk1LiveSupport.assumeRestAvailable()
        every { deviceDirectory.findMikrotikDeviceRef(1L) } returns Mk1LiveSupport.restDevice()

        val result = adapter.poll(target)

        assertEquals("SUCCESS", result.status, result.probeRun.error)
        assertNotNull(result.snapshot)
        assertTrue(result.snapshot!!.interfaces.isNotEmpty())
        assertTrue(result.snapshot!!.interfaces.any { it.name == "sfp-sfpplus1" })
        assertNotNull(result.probeRun.latencyMs)
        assertTrue(result.probeRun.latencyMs!! < 30000)
    }

    private class LiveDelegatingSession(
        private val client: RouterOs7RestAdapter,
        private val device: MikrotikDeviceRef,
    ) : MikrotikSession {
        override fun print(path: String, query: Map<String, String>, proplist: List<String>): List<Map<String, String>> {
            return client.withSession(device) { it.print(path, query, proplist) }
        }

        override fun call(path: String, args: Map<String, String>): List<Map<String, String>> {
            return client.withSession(device) { it.call(path, args) }
        }

        override fun add(path: String, args: Map<String, String>) {
            client.withSession(device) { it.add(path, args) }
        }

        override fun set(path: String, id: String, args: Map<String, String>) {
            client.withSession(device) { it.set(path, id, args) }
        }

        override fun remove(path: String, id: String) {
            client.withSession(device) { it.remove(path, id) }
        }

        override fun execute(command: String): List<Map<String, String>> {
            throw MikrotikCommandException("Raw execute is not supported: $command")
        }
    }
}
