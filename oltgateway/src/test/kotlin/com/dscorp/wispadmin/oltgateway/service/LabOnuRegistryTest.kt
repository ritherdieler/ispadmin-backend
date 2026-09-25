package com.dscorp.wispadmin.oltgateway.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LabOnuRegistryTest {

    private val registry = InMemoryLabOnuRegistry()

    @Test
    fun `un serial guardado es lab y uno desconocido no`() {
        registry.add("HWTCNEWLAB01")

        assertTrue(registry.isLab("HWTCNEWLAB01"))
        assertFalse(registry.isLab("HWTC12345678"))
    }

    @Test
    fun `un sufijo guardado coincide con el serial completo`() {
        registry.add("0031C0B6")

        assertTrue(registry.isLab("VSOL0031C0B6"))
    }

    @Test
    fun `quitar es explicito y el alta no se pierde sola`() {
        registry.add("ZTEGDC47BFFD")

        assertEquals(listOf("ZTEGDC47BFFD"), registry.list().map { it.sn })
        registry.remove("ZTEGDC47BFFD")

        assertFalse(registry.isLab("ZTEGDC47BFFD"))
    }
}
