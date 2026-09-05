package com.dscorp.wispadmin.netdiag.client

import com.dscorp.wispadmin.netdiag.port.OltCliOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetDiagOltGatewayHttpClientTest {

    @Test
    fun `degrada si olt gateway internal-base-url esta vacio`() {
        val client = NetDiagOltGatewayHttpClient(baseUrl = "", apiKey = "k")
        assertEquals("unavailable", client.descriptor().oltId)
        assertEquals(emptyList<Any>(), client.listOnusOnPon("olt", 0, 1))
        assertTrue(client.runAlarmPoll() is OltCliOutcome.Skipped)
        assertEquals(emptyList<Any>(), client.parseActiveAlarms("raw"))
    }
}
