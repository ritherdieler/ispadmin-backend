package com.dscorp.wispadmin.oltgateway.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpticalInfoParserTest {

    private val parser = OpticalInfoParser()

    @Test
    fun `parsea display ont optical-info legacy sin oltRx`() {
        val output = FixtureLoader.load("display-ont-optical-info.txt")

        val result = parser.parse(output, ontId = 5)

        assertEquals(5, result.ontId)
        assertEquals(-18.234, result.rxPowerDbm!!, 0.001)
        assertEquals(2.145, result.txPowerDbm!!, 0.001)
        assertNull(result.oltRxPowerDbm)
        assertEquals(45.12, result.temperatureC!!, 0.01)
        assertEquals(3.280, result.voltageV!!, 0.001)
        assertEquals(15.000, result.biasCurrentMa!!, 0.001)
    }

    @Test
    fun `parsea fila live con oltRxPowerDbm`() {
        val output = FixtureLoader.load("display-ont-optical-info-all-live.txt")

        val result = parser.parse(output, ontId = 1)

        assertEquals(1, result.ontId)
        assertEquals(-18.54, result.rxPowerDbm!!, 0.001)
        assertEquals(2.20, result.txPowerDbm!!, 0.001)
        assertEquals(-24.82, result.oltRxPowerDbm!!, 0.001)
        assertEquals(57.0, result.temperatureC!!, 0.01)
        assertEquals(3.220, result.voltageV!!, 0.001)
        assertEquals(13.0, result.biasCurrentMa!!, 0.001)
    }

    @Test
    fun `parseAll lee tabla optical-info port all live`() {
        val output = FixtureLoader.load("display-ont-optical-info-all-live.txt")

        val results = parser.parseAll(output)

        assertTrue(results.size >= 30)
        val ont1 = results.first { it.ontId == 1 }
        assertEquals(-18.54, ont1.rxPowerDbm!!, 0.001)
        assertEquals(2.20, ont1.txPowerDbm!!, 0.001)
        assertEquals(-24.82, ont1.oltRxPowerDbm!!, 0.001)
        assertEquals(57.0, ont1.temperatureC!!, 0.01)

        val ont2 = results.first { it.ontId == 2 }
        assertEquals(-25.08, ont2.rxPowerDbm!!, 0.001)
        assertEquals(-30.46, ont2.oltRxPowerDbm!!, 0.001)

        assertTrue(results.none { it.ontId == 8 })
        assertEquals(results.map { it.ontId }, results.map { it.ontId }.distinct())
    }

    @Test
    fun `parseAll en fixture legacy mantiene compat`() {
        val output = FixtureLoader.load("display-ont-optical-info.txt")

        val results = parser.parseAll(output)

        assertEquals(1, results.size)
        assertEquals(5, results[0].ontId)
        assertEquals(-18.234, results[0].rxPowerDbm!!, 0.001)
        assertNull(results[0].oltRxPowerDbm)
    }
}
