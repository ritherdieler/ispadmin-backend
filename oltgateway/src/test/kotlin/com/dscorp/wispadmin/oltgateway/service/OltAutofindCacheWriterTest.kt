package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuAutofind
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuAutofindRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.dscorp.wispadmin.oltgateway.parser.ParsedAutofindOnt
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional

class OltAutofindCacheWriterTest {

    private val autofindRepository = mockk<OltMgrOnuAutofindRepository>(relaxed = true)
    private val onuRepository = mockk<OltMgrOnuRepository>(relaxed = true)
    private val properties = OltGatewayProperties().apply { oltId = "gigafiber-ma5608t" }

    private val writer = OltAutofindCacheWriter(autofindRepository, onuRepository, properties)

    private fun ont(sn: String, slot: Int = 0, port: Int = 1) = ParsedAutofindOnt(
        sn = sn,
        frame = 0,
        slot = slot,
        port = port,
        vendorId = "HWTC",
        equipmentId = "EG8145V5"
    )

    private fun cached(sn: String, board: Int = 0, port: Int = 1) = OltMgrOnuAutofind(
        id = 1L,
        sn = sn,
        board = board,
        port = port,
        equipmentId = "EG8145V5",
        firstSeenAt = Instant.now(),
        lastSeenAt = Instant.now()
    )

    @BeforeEach
    fun setup() {
        every { autofindRepository.findBySnIgnoreCase(any()) } returns Optional.empty()
        every { autofindRepository.deleteBySnUpperNotIn(any()) } returns 0
        every { autofindRepository.count() } returns 0
        every { autofindRepository.save(any()) } answers { firstArg() }
        every { onuRepository.findExistingSnsUpper(any()) } returns emptyList()
    }

    @Test
    fun `guarda el snapshot normalizando el serial y marcando el origen`() {
        val saved = slot<OltMgrOnuAutofind>()
        every { autofindRepository.save(capture(saved)) } answers { saved.captured }

        val result = writer.replaceSnapshot(listOf(ont("hwtc0086cd49", slot = 1, port = 3)), "background")

        assertEquals(1, result.stored)
        assertEquals("HWTC0086CD49", saved.captured.sn)
        assertEquals(1, saved.captured.board)
        assertEquals(3, saved.captured.port)
        assertEquals("background", saved.captured.source)
    }

    @Test
    fun `reutiliza la fila existente para conservar el primer avistamiento`() {
        val existing = cached("HWTC0086CD49").apply { firstSeenAt = Instant.parse("2026-08-01T00:00:00Z") }
        every { autofindRepository.findBySnIgnoreCase("HWTC0086CD49") } returns Optional.of(existing)
        val saved = slot<OltMgrOnuAutofind>()
        every { autofindRepository.save(capture(saved)) } answers { saved.captured }

        writer.replaceSnapshot(listOf(ont("HWTC0086CD49")), "live")

        assertEquals(Instant.parse("2026-08-01T00:00:00Z"), saved.captured.firstSeenAt)
        assertTrue(saved.captured.lastSeenAt.isAfter(saved.captured.firstSeenAt))
    }

    @Test
    fun `poda los seriales que ya no aparecen en el autofind`() {
        every { autofindRepository.deleteBySnUpperNotIn(setOf("HWTC0086CD49")) } returns 2

        val result = writer.replaceSnapshot(listOf(ont("HWTC0086CD49")), "background")

        assertEquals(2, result.removed)
        verify(exactly = 1) { autofindRepository.deleteBySnUpperNotIn(setOf("HWTC0086CD49")) }
    }

    @Test
    fun `un autofind vacio limpia el cache completo`() {
        every { autofindRepository.count() } returns 3

        val result = writer.replaceSnapshot(emptyList(), "background")

        assertEquals(0, result.stored)
        assertEquals(3, result.removed)
        verify(exactly = 1) { autofindRepository.deleteAllInBatch() }
        verify(exactly = 0) { autofindRepository.deleteBySnUpperNotIn(any()) }
    }

    @Test
    fun `no lista las ONU que ya estan configuradas en el inventario`() {
        every { autofindRepository.findAllByOrderByLastSeenAtDesc() } returns listOf(
            cached("HWTC0086CD49"),
            cached("HWTC11112222", board = 1, port = 5)
        )
        every { onuRepository.findExistingSnsUpper(any()) } returns listOf("HWTC0086CD49")

        val items = writer.listUnconfigured()

        assertEquals(1, items.size)
        assertEquals("HWTC11112222", items[0].sn)
        assertEquals("1", items[0].board)
        assertEquals("5", items[0].port)
        assertEquals("gigafiber-ma5608t", items[0].olt_id)
    }

    @Test
    fun `sin cache no consulta el inventario`() {
        every { autofindRepository.findAllByOrderByLastSeenAtDesc() } returns emptyList()

        assertTrue(writer.listUnconfigured().isEmpty())
        verify(exactly = 0) { onuRepository.findExistingSnsUpper(any()) }
    }
}
