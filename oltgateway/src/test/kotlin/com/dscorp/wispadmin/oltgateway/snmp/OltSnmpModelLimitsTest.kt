package com.dscorp.wispadmin.oltgateway.snmp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OltSnmpModelLimitsTest {

    @Test
    fun `MA5608T solo admite 1 walk SNMP concurrente`() {
        assertEquals(1, OltSnmpModelLimits.maxConcurrentWalks("MA5608T"))
        assertEquals(1, OltSnmpModelLimits.maxConcurrentWalks("ma5608t"))
    }

    @Test
    fun `modelo desconocido usa limite conservador 1`() {
        assertEquals(1, OltSnmpModelLimits.maxConcurrentWalks("UNKNOWN-OLT"))
        assertEquals(1, OltSnmpModelLimits.maxConcurrentWalks(null))
        assertEquals(1, OltSnmpModelLimits.maxConcurrentWalks("  "))
    }
}
