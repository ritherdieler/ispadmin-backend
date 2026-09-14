package com.dscorp.wispadmin.routeros

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RouterOsTrafficCounterParserTest {

    @Test
    fun `parseUpDown parses upload download pair`() {
        val parsed = RouterOsTrafficCounterParser.parseUpDown("1000/2000")
        assertEquals(1000L, parsed?.first)
        assertEquals(2000L, parsed?.second)
    }

    @Test
    fun `parseUpDown returns null for blank`() {
        assertNull(RouterOsTrafficCounterParser.parseUpDown(""))
        assertNull(RouterOsTrafficCounterParser.parseUpDown("  "))
    }

    @Test
    fun `parseUpDown parses single numeric value as both sides`() {
        val parsed = RouterOsTrafficCounterParser.parseUpDown("5000")
        assertEquals(5000L, parsed?.first)
        assertEquals(5000L, parsed?.second)
    }

    @Test
    fun `normalizeTarget strips cidr suffix`() {
        assertEquals("10.1.2.3", RouterOsTrafficCounterParser.normalizeTarget("10.1.2.3/32"))
        assertEquals("10.1.2.3", RouterOsTrafficCounterParser.normalizeTarget("10.1.2.3"))
    }

    @Test
    fun `normalizeTarget uses first address of a multi target queue`() {
        assertEquals(
            "192.168.250.20",
            RouterOsTrafficCounterParser.normalizeTarget("192.168.250.20/32,10.0.0.1/32,192.168.1.1"),
        )
    }

    @Test
    fun `normalizeTarget skips targets longer than 45 chars after normalize`() {
        val longHost = "host-that-is-clearly-longer-than-forty-five-characters.example"
        assertNull(RouterOsTrafficCounterParser.normalizeTarget(longHost))
    }

    @Test
    fun `computeDelta returns positive delta`() {
        val delta = RouterOsTrafficCounterParser.computeDelta(previous = 100L, current = 250L, counterReset = false)
        assertEquals(150L, delta)
    }

    @Test
    fun `computeDelta returns zero on counter reset`() {
        assertEquals(0L, RouterOsTrafficCounterParser.computeDelta(previous = 100L, current = 50L, counterReset = true))
    }

    @Test
    fun `computeDelta returns zero when current below previous without reset`() {
        assertEquals(0L, RouterOsTrafficCounterParser.computeDelta(previous = 100L, current = 50L, counterReset = false))
    }

    @Test
    fun `bytesToMbps converts over interval`() {
        val mbps = RouterOsTrafficCounterParser.bytesToMbps(bytesDelta = 625_000L, intervalSeconds = 300.0)
        assertEquals(0.0167, mbps, 0.0001)
    }
}
