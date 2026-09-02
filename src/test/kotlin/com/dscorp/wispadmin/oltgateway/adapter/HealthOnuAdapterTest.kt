package com.dscorp.wispadmin.oltgateway.adapter

import com.dscorp.wispadmin.oltgateway.port.OltInventoryPort
import com.dscorp.wispadmin.oltgateway.port.OltOnuSnapshot
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HealthOnuAdapterTest {

    private val inventory = mockk<OltInventoryPort>()
    private val adapter = HealthOnuAdapter(inventory)

    private val snapshot = OltOnuSnapshot(
        id = 11L,
        sn = "HWTC1",
        externalId = "ext-1",
        oltId = 4L,
        oltName = "olt-a",
        board = 0,
        port = 2,
        onuIndex = 8
    )

    @Test
    fun `findBySn maps inventory snapshot to a flat ref`() {
        every { inventory.findBySn("HWTC1") } returns snapshot

        val ref = adapter.findBySn("HWTC1")

        assertEquals(11L, ref?.id)
        assertEquals("HWTC1", ref?.sn)
        assertEquals("ext-1", ref?.externalId)
        assertEquals(4L, ref?.oltId)
        assertEquals("olt-a", ref?.oltName)
        assertEquals(2, ref?.port)
    }

    @Test
    fun `findByExternalId returns null when inventory misses`() {
        every { inventory.findByExternalId("missing") } returns null
        assertNull(adapter.findByExternalId("missing"))
    }

    @Test
    fun `findOltIdByName delegates to inventory`() {
        every { inventory.findOltIdByName("olt-a") } returns 4L
        assertEquals(4L, adapter.findOltIdByName("olt-a"))
    }
}
