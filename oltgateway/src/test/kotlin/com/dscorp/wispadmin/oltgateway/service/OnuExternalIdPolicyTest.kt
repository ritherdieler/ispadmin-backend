package com.dscorp.wispadmin.oltgateway.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OnuExternalIdPolicyTest {

    @Test
    fun `el identificador propio es determinista por posicion`() {
        assertEquals(
            "gigafiber-ma5608t_1_0_5",
            OnuExternalIdPolicy.canonical("gigafiber-ma5608t", 1, 0, 5)
        )
    }

    @Test
    fun `reconoce los identificadores que ya tienen el formato propio`() {
        assertTrue(OnuExternalIdPolicy.isCanonical("gigafiber-ma5608t_1_0_5", "gigafiber-ma5608t"))
    }

    @Test
    fun `no reconoce los identificadores que genero SmartOLT`() {
        assertFalse(OnuExternalIdPolicy.isCanonical("184", "gigafiber-ma5608t"))
        assertFalse(OnuExternalIdPolicy.isCanonical("", "gigafiber-ma5608t"))
        assertFalse(OnuExternalIdPolicy.isCanonical("otra-olt_1_0_5", "gigafiber-ma5608t"))
        assertFalse(OnuExternalIdPolicy.isCanonical("gigafiber-ma5608t_1_0", "gigafiber-ma5608t"))
        assertFalse(OnuExternalIdPolicy.isCanonical("gigafiber-ma5608t_1_0_x", "gigafiber-ma5608t"))
    }

    @Test
    fun `tolera un oltId con guiones bajos`() {
        assertTrue(OnuExternalIdPolicy.isCanonical("olt_lima_norte_1_0_5", "olt_lima_norte"))
        assertEquals("olt_lima_norte_1_0_5", OnuExternalIdPolicy.canonical("olt_lima_norte", 1, 0, 5))
    }
}
