package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTarget
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagTargetRepository
import com.dscorp.wispadmin.netdiag.port.NetDiagOltDescriptor
import com.dscorp.wispadmin.netdiag.port.NetDiagOltDescriptorPort
import com.dscorp.wispadmin.netdiag.port.NetDiagOltInventoryPort
import com.dscorp.wispadmin.netdiag.port.NetDiagPonOnu
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.util.Optional
import java.util.concurrent.atomic.AtomicLong

class OltNetDiagTargetSyncServiceTest {

    private val targetRepository = mockk<NetDiagTargetRepository>()
    private val objectMapper = ObjectMapper()
    private val inventory = object : NetDiagOltInventoryPort {
        var oltPk: Long? = 9L
        override fun findOltId(name: String): Long? = if (name == "gigafiber-ma5608t") oltPk else null
        override fun listOnusOnPon(oltName: String, board: Int, port: Int): List<NetDiagPonOnu> = emptyList()
        override fun findOnu(oltName: String, board: Int, port: Int, onuIndex: Int): NetDiagPonOnu? = null
    }
    private val descriptor = object : NetDiagOltDescriptorPort {
        override fun descriptor() = NetDiagOltDescriptor(
            oltId = "gigafiber-ma5608t",
            host = "10.11.104.2",
            alarmPollEnabled = true,
            portsPerGponBoard = 16
        )
    }
    private val service = OltNetDiagTargetSyncService(
        targetRepository = targetRepository,
        inventoryProvider = availableProvider(inventory),
        descriptorProvider = availableProvider(descriptor),
        objectMapper = objectMapper
    )
    private val idSeq = AtomicLong(1)
    private val saved = mutableListOf<NetDiagTarget>()

    @BeforeEach
    fun setup() {
        saved.clear()
        inventory.oltPk = 9L
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
        inventory.oltPk = null

        val result = service.sync()

        assertEquals(0, result.upserted)
        verify(exactly = 0) { targetRepository.save(any()) }
    }

    @Test
    fun `no hace nada si oltgateway no esta presente`() {
        val isolated = OltNetDiagTargetSyncService(
            targetRepository = targetRepository,
            inventoryProvider = emptyProvider(),
            descriptorProvider = emptyProvider(),
            objectMapper = objectMapper
        )

        val result = isolated.sync()

        assertEquals(0, result.upserted)
        verify(exactly = 0) { targetRepository.save(any()) }
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
