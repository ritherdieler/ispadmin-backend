package com.dscorp.wispadmin.oltgateway.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OntIdAllocatorTest {

    @Test
    fun `sin ocupacion live usa dbMax mas uno`() {
        assertEquals(7, OntIdAllocator.nextFree(liveOccupied = emptySet(), dbMax = 6))
    }

    @Test
    fun `inventario staging vacio no pisa ontId live ocupado`() {
        assertEquals(2, OntIdAllocator.nextFree(liveOccupied = setOf(0, 1), dbMax = 0))
    }

    @Test
    fun `puerto vacio arranca en cero`() {
        assertEquals(0, OntIdAllocator.nextFree(liveOccupied = emptySet(), dbMax = -1))
    }

    @Test
    fun `sin hueco lanza`() {
        assertThrows<IllegalStateException> {
            OntIdAllocator.nextFree(liveOccupied = setOf(0, 1), dbMax = -1, maxOntId = 1)
        }
    }
}
