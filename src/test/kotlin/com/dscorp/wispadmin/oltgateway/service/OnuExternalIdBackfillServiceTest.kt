package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOlt
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional

class OnuExternalIdBackfillServiceTest {

    private val oltRepository = mockk<OltMgrOltRepository>()
    private val onuRepository = mockk<OltMgrOnuRepository>(relaxed = true)
    private val properties = OltGatewayProperties().apply { oltId = "gigafiber-ma5608t" }
    private lateinit var service: OnuExternalIdBackfillService

    private val olt = OltMgrOlt(id = 1L, name = "gigafiber-ma5608t", ipAddress = "10.11.104.2")

    private fun onu(id: Long, externalId: String, board: Int, port: Int, index: Int, deleted: Boolean = false) =
        OltMgrOnu(
            id = id,
            sn = "SN$id",
            externalId = externalId,
            olt = olt,
            board = board,
            port = port,
            onuIndex = index
        ).apply { if (deleted) deletedAt = Instant.now() }

    @BeforeEach
    fun setUp() {
        service = OnuExternalIdBackfillService(oltRepository, onuRepository, properties)
        every { oltRepository.findByName("gigafiber-ma5608t") } returns Optional.of(olt)
        every { onuRepository.findByExternalId(any()) } returns Optional.empty()
        every { onuRepository.save(any()) } answers { firstArg() }
    }

    @Test
    fun `reescribe los identificadores que genero SmartOLT`() {
        val legacy = onu(10L, "184", board = 1, port = 0, index = 5)
        every { onuRepository.findByOlt_Id(1L) } returns listOf(legacy)

        val result = service.backfill()

        assertEquals("gigafiber-ma5608t_1_0_5", legacy.externalId)
        assertEquals(1, result.scanned)
        assertEquals(1, result.rewritten)
        verify { onuRepository.save(legacy) }
    }

    @Test
    fun `no toca los identificadores que ya son propios`() {
        val own = onu(11L, "gigafiber-ma5608t_1_0_5", board = 1, port = 0, index = 5)
        every { onuRepository.findByOlt_Id(1L) } returns listOf(own)

        val result = service.backfill()

        assertEquals(0, result.rewritten)
        verify(exactly = 0) { onuRepository.save(any()) }
    }

    @Test
    fun `tampoco regenera el identificador propio de una ONU que fue movida`() {
        val moved = onu(12L, "gigafiber-ma5608t_0_2_5", board = 1, port = 0, index = 5)
        every { onuRepository.findByOlt_Id(1L) } returns listOf(moved)

        service.backfill()

        assertEquals("gigafiber-ma5608t_0_2_5", moved.externalId)
        verify(exactly = 0) { onuRepository.save(any()) }
    }

    @Test
    fun `salta las ONU borradas`() {
        every { onuRepository.findByOlt_Id(1L) } returns listOf(onu(13L, "184", 1, 0, 5, deleted = true))

        val result = service.backfill()

        assertEquals(0, result.scanned)
        assertEquals(0, result.rewritten)
        verify(exactly = 0) { onuRepository.save(any()) }
    }

    @Test
    fun `no reescribe cuando el identificador propio ya lo ocupa otra ONU`() {
        val legacy = onu(14L, "184", board = 1, port = 0, index = 5)
        every { onuRepository.findByOlt_Id(1L) } returns listOf(legacy)
        every { onuRepository.findByExternalId("gigafiber-ma5608t_1_0_5") } returns
            Optional.of(onu(99L, "gigafiber-ma5608t_1_0_5", 1, 0, 5))

        val result = service.backfill()

        assertEquals("184", legacy.externalId)
        assertEquals(1, result.collisions)
        assertEquals(0, result.rewritten)
        verify(exactly = 0) { onuRepository.save(any()) }
    }

    @Test
    fun `sin OLT sembrada no hace nada`() {
        every { oltRepository.findByName("gigafiber-ma5608t") } returns Optional.empty()

        val result = service.backfill()

        assertEquals(0, result.scanned)
        verify(exactly = 0) { onuRepository.findByOlt_Id(any()) }
    }
}
