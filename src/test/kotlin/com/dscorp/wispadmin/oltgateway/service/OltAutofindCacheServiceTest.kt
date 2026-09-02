package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.parser.ParsedAutofindOnt
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OltAutofindCacheServiceTest {

    private val queryFacade = mockk<OltGatewayQueryFacade>()
    private val writer = mockk<OltAutofindCacheWriter>(relaxed = true)
    private val properties = OltGatewayProperties().apply {
        oltId = "gigafiber-ma5608t"
        autofind.liveTimeoutMs = 10_000
    }

    private val service = OltAutofindCacheService(queryFacade, writer, properties)

    private fun ont(sn: String, slot: Int = 0, port: Int = 1) = ParsedAutofindOnt(
        sn = sn,
        frame = 0,
        slot = slot,
        port = port,
        vendorId = "HWTC",
        equipmentId = "EG8145V5",
        softwareVersion = null,
        autofindTime = null
    )

    @BeforeEach
    fun setup() {
        every { writer.replaceSnapshot(any(), any()) } returns OltAutofindCacheWriter.StoreResult(0, 0)
        every { writer.cachedCount() } returns 0
    }

    @Test
    fun `el refresco periodico lee por el carril de fondo`() {
        every { queryFacade.autofindParsedBackground() } returns listOf(ont("HWTC11112222"))
        every { writer.replaceSnapshot(any(), any()) } returns OltAutofindCacheWriter.StoreResult(1, 0)

        val result = service.refresh()

        verify(exactly = 1) { queryFacade.autofindParsedBackground() }
        verify(exactly = 0) { queryFacade.autofindParsedLive(any()) }
        verify { writer.replaceSnapshot(any(), OltAutofindCacheService.SOURCE_BACKGROUND) }
        assertEquals(1, result.seen)
        assertEquals(1, result.stored)
        assertNull(result.error)
    }

    @Test
    fun `el refresco forzado lee en vivo con el timeout duro configurado`() {
        properties.autofind.liveTimeoutMs = 7_500
        every { queryFacade.autofindParsedLive(7_500) } returns listOf(ont("HWTC33334444"))

        service.refreshLive()

        verify(exactly = 1) { queryFacade.autofindParsedLive(7_500) }
        verify(exactly = 0) { queryFacade.autofindParsedBackground() }
        verify { writer.replaceSnapshot(any(), OltAutofindCacheService.SOURCE_LIVE) }
    }

    @Test
    fun `un fallo de la OLT no propaga excepcion y queda registrado`() {
        every { queryFacade.autofindParsedBackground() } throws IllegalStateException("cli_bus_busy")

        val result = service.refresh()

        assertEquals("cli_bus_busy", result.error)
        verify(exactly = 0) { writer.replaceSnapshot(any(), any()) }
        assertNotNull(service.status().lastRefreshAt)
    }

    @Test
    fun `el estado expone el resultado del ultimo refresco`() {
        every { queryFacade.autofindParsedBackground() } returns listOf(ont("HWTC55556666"))
        every { writer.replaceSnapshot(any(), any()) } returns OltAutofindCacheWriter.StoreResult(1, 2)
        every { writer.cachedCount() } returns 1

        service.refresh()
        val status = service.status()

        assertEquals(1L, status.cachedCount)
        assertEquals(1, status.lastResult?.stored)
        assertEquals(2, status.lastResult?.removed)
    }
}
