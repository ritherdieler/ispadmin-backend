package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagProbeRun
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTarget
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagProbeRunRepository
import com.dscorp.wispadmin.netdiag.port.NetDiagDeviceDirectoryPort
import com.dscorp.wispadmin.routeros.Mk1LiveSupport
import com.dscorp.wispadmin.routeros.adapter.LegrangeClassicAdapter
import com.dscorp.wispadmin.routeros.adapter.RouterOs7RestAdapter
import com.dscorp.wispadmin.routeros.adapter.RouterOsRestClassicFallbackAdapter
import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
    private val adapter = MikrotikPollAdapter(
        mikrotikClient = RouterOs7RestAdapter(Mk1LiveSupport.liveRestProperties(timeoutMs = 30000)),
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

    @Test
    fun `poll MK1 via netdiag fallback client matches application-dev wiring`() {
        Mk1LiveSupport.assumeRestAvailable()
        every { deviceDirectory.findMikrotikDeviceRef(1L) } returns Mk1LiveSupport.restDevice()

        val properties = RouterOsClientProperties().apply {
            rest.port = 443
            rest.verifySsl = true
            rest.trustStore = "classpath:routeros-mk-truststore.jks"
            rest.trustStorePassword = "changeit"
            rest.timeoutMs = 10000
            classic.port = 8728
        }
        @Suppress("DEPRECATION")
        val fallbackAdapter = MikrotikPollAdapter(
            mikrotikClient = RouterOsRestClassicFallbackAdapter(
                primary = RouterOs7RestAdapter(properties, objectMapper),
                fallback = LegrangeClassicAdapter(properties)
            ),
            deviceDirectory = deviceDirectory,
            probeRunRepository = probeRunRepository,
            objectMapper = objectMapper,
            netwatchAdapter = MikrotikNetwatchAdapter(),
            opticalAdapter = MikrotikOpticalAdapter()
        )

        val result = fallbackAdapter.poll(target)

        assertEquals("SUCCESS", result.status, result.probeRun.error)
        assertTrue(result.snapshot!!.interfaces.any { it.name == "sfp-sfpplus1" })
    }
}
