package com.dscorp.wispadmin.oltgateway.snmp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SnmpOpticalMergerTest {

    @Test
    fun `merge une columnas por SnmpOntKey`() {
        val key = SnmpOntKey(ifIndex = 4_194_304_000L, ontId = 0)
        val merged = SnmpOpticalMerger.merge(
            rx = mapOf(key to -5.62),
            tx = mapOf(key to 1.63),
            oltRx = mapOf(key to -26.39),
            temperatureC = mapOf(key to 48.5),
            biasCurrentMa = mapOf(key to 10.0),
            distanceM = mapOf(key to 795)
        )
        assertEquals(1, merged.size)
        val row = merged.single()
        assertEquals(-5.62, row.onuRxDbm)
        assertEquals(1.63, row.onuTxDbm)
        assertEquals(-26.39, row.oltRxDbm)
        assertEquals(48.5, row.temperatureC)
        assertEquals(10.0, row.biasCurrentMa)
        assertEquals(795, row.distanceM)
    }

    @Test
    fun `merge tolera claves parciales entre columnas`() {
        val k1 = SnmpOntKey(100L, 1)
        val k2 = SnmpOntKey(100L, 2)
        val merged = SnmpOpticalMerger.merge(
            rx = mapOf(k1 to -10.0, k2 to -11.0),
            tx = mapOf(k1 to 2.0),
            oltRx = emptyMap(),
            temperatureC = mapOf(k2 to 40.0),
            biasCurrentMa = emptyMap(),
            distanceM = emptyMap()
        )
        assertEquals(2, merged.size)
        val row2 = merged.first { it.key.ontId == 2 }
        assertNull(row2.onuTxDbm)
        assertEquals(-11.0, row2.onuRxDbm)
        assertEquals(40.0, row2.temperatureC)
    }
}
