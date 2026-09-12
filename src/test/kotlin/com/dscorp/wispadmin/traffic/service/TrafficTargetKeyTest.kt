package com.dscorp.wispadmin.traffic.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrafficTargetKeyTest {

    @Test
    fun `la clave de un objetivo con IP estatica es la IP`() {
        assertEquals("192.168.25.10", TrafficTargetKey.of(ip = "192.168.25.10", pppoeUsername = null))
    }

    @Test
    fun `la clave de un objetivo PPPoE es el username prefijado`() {
        assertEquals("pppoe:gf4321", TrafficTargetKey.of(ip = "10.64.3.7", pppoeUsername = "gf4321"))
    }

    @Test
    fun `el username tiene prioridad sobre una IP vacia`() {
        assertEquals("pppoe:gf4321", TrafficTargetKey.of(ip = "", pppoeUsername = "gf4321"))
    }

    @Test
    fun `sin IP ni username no hay clave`() {
        assertNull(TrafficTargetKey.of(ip = "   ", pppoeUsername = " "))
    }

    @Test
    fun `una cola dinamica de PPPoE se identifica por su nombre`() {
        assertEquals(
            "pppoe:gf4321",
            TrafficTargetKey.ofQueue(name = "<pppoe-gf4321>", target = "10.64.3.7/32")
        )
    }

    @Test
    fun `una cola normal se identifica por su target`() {
        assertEquals(
            "192.168.25.10",
            TrafficTargetKey.ofQueue(name = "prod-100-JUAN", target = "192.168.25.10/32")
        )
    }

    @Test
    fun `una cola sin target ni nombre PPPoE no tiene clave`() {
        assertNull(TrafficTargetKey.ofQueue(name = "prod-100-JUAN", target = null))
    }

    @Test
    fun `reconoce las claves PPPoE y extrae el username`() {
        assertTrue(TrafficTargetKey.isPppoe("pppoe:gf4321"))
        assertEquals("gf4321", TrafficTargetKey.username("pppoe:gf4321"))
        assertFalse(TrafficTargetKey.isPppoe("192.168.25.10"))
        assertNull(TrafficTargetKey.username("192.168.25.10"))
    }

    @Test
    fun `la IP visible de una cola PPPoE sigue siendo el target`() {
        assertEquals("10.64.3.7", TrafficTargetKey.queueAddress(target = "10.64.3.7/32"))
        assertNull(TrafficTargetKey.queueAddress(target = null))
    }
}
