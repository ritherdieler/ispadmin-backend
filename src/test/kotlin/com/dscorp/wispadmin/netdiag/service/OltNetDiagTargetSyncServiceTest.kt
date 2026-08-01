package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTarget
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagTargetRepository
import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOlt
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.concurrent.atomic.AtomicLong

class OltNetDiagTargetSyncServiceTest {

    private val targetRepository = mockk<NetDiagTargetRepository>()
    private val oltRepository = mockk<OltMgrOltRepository>()
    private val objectMapper = ObjectMapper()
    private val properties = OltGatewayProperties().apply {
        oltId = "gigafiber-ma5608t"
        host = "10.11.104.2"
        inventory.defaultPortsPerGponBoard = 16
    }
    private val service = OltNetDiagTargetSyncService(
        targetRepository = targetRepository,
        oltRepository = oltRepository,
        oltGatewayProperties = properties,
        objectMapper = objectMapper
    )
    private val idSeq = AtomicLong(1)
    private val saved = mutableListOf<NetDiagTarget>()

    @BeforeEach
    fun setup() {
        saved.clear()
        every { targetRepository.findByName(any()) } returns Optional.empty()
        every { targetRepository.save(any()) } answers {
            firstArg<NetDiagTarget>().also {
                if (it.id == null) it.id = idSeq.incrementAndGet()
                saved += it
            }
        }
    }

    @Test
    fun `crea target OLT y 32 targets PON con parent y monitor_config`() {
        val olt = OltMgrOlt(id = 9L, name = "gigafiber-ma5608t", ipAddress = "10.11.104.2")
        every { oltRepository.findByName("gigafiber-ma5608t") } returns Optional.of(olt)

        val result = service.sync()

        assertEquals(33, result.upserted)
        assertEquals(1, result.oltTargets)
        assertEquals(32, result.ponTargets)

        val oltTarget = saved.first { it.name == "OLT-gigafiber-ma5608t" }
        assertEquals(9L, oltTarget.deviceRefId)
        assertTrue(oltTarget.enabled)
        assertTrue(oltTarget.monitorConfig!!.contains("\"kind\":\"olt\""))
        assertTrue(oltTarget.monitorConfig!!.contains("\"mgmtIp\":\"10.11.104.2\""))

        val pon = saved.first { it.name == "PON-gigafiber-ma5608t-gpon-0/5" }
        assertEquals(oltTarget.id, pon.parentTargetId)
        assertTrue(pon.monitorConfig!!.contains("\"kind\":\"pon\""))
        assertTrue(pon.monitorConfig!!.contains("\"board\":0"))
        assertTrue(pon.monitorConfig!!.contains("\"port\":5"))

        val boards = saved.filter { it.name.startsWith("PON-") }
            .map { Regex("""gpon-(\d+)/(\d+)""").find(it.name)!!.groupValues.let { g -> g[1].toInt() to g[2].toInt() } }
            .toSet()
        assertEquals((0..1).flatMap { b -> (0..15).map { b to it } }.toSet(), boards)
    }

    @Test
    fun `actualiza target OLT existente sin duplicar`() {
        val olt = OltMgrOlt(id = 9L, name = "gigafiber-ma5608t", ipAddress = "10.11.104.2")
        every { oltRepository.findByName("gigafiber-ma5608t") } returns Optional.of(olt)
        val existing = NetDiagTarget(
            id = 42L,
            name = "OLT-gigafiber-ma5608t",
            deviceRefId = 1L,
            enabled = true,
            monitorConfig = """{"kind":"olt","oltId":"old"}"""
        )
        every { targetRepository.findByName("OLT-gigafiber-ma5608t") } returns Optional.of(existing)
        every { targetRepository.findByName(match { it.startsWith("PON-") }) } returns Optional.empty()

        val result = service.sync()

        assertEquals(33, result.upserted)
        assertEquals(42L, existing.id)
        assertEquals(9L, existing.deviceRefId)
        assertTrue(existing.monitorConfig!!.contains("\"mgmtIp\":\"10.11.104.2\""))
        assertEquals(1, saved.count { it.id == 42L })
        assertEquals(32, saved.count { it.name.startsWith("PON-") })
    }

    @Test
    fun `no hace nada si la OLT no esta seed`() {
        every { oltRepository.findByName("gigafiber-ma5608t") } returns Optional.empty()

        val result = service.sync()

        assertEquals(0, result.upserted)
        verify(exactly = 0) { targetRepository.save(any()) }
    }
}
