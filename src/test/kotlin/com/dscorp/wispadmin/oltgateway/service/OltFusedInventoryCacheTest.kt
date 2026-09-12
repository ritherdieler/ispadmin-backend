package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.parser.ParsedOnuSummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class OltFusedInventoryCacheTest {

    private var now = Instant.parse("2026-09-09T18:00:00Z")
    private val cache = OltFusedInventoryCache { now }

    private val onus = listOf(
        ParsedOnuSummary(frame = 0, slot = 0, port = 1, ontId = 3, sn = "VSOL0086F6E9")
    )

    @Test
    fun `entrega el snapshot publicado si esta fresco`() {
        cache.publish(onus)
        now = now.plusSeconds(60)

        val taken = cache.takeIfFresh(maxAgeMs = 900_000)

        assertNotNull(taken)
        assertEquals(onus, taken!!.onus)
    }

    @Test
    fun `el snapshot se consume una sola vez`() {
        cache.publish(onus)

        assertNotNull(cache.takeIfFresh(maxAgeMs = 900_000))
        assertNull(cache.takeIfFresh(maxAgeMs = 900_000), "a snapshot must not be persisted twice")
    }

    @Test
    fun `descarta el snapshot caducado`() {
        cache.publish(onus)
        now = now.plusSeconds(1_000)

        assertNull(cache.takeIfFresh(maxAgeMs = 900_000))
    }

    @Test
    fun `sin publicacion previa no entrega nada`() {
        assertNull(cache.takeIfFresh(maxAgeMs = 900_000))
    }

    @Test
    fun `publicar un snapshot vacio no deja nada que consumir`() {
        cache.publish(emptyList())

        assertNull(cache.takeIfFresh(maxAgeMs = 900_000))
    }
}
