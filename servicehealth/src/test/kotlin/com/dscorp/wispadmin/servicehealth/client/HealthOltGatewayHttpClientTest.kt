package com.dscorp.wispadmin.servicehealth.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HealthOltGatewayHttpClientTest {

    @Test
    fun `degrada a null si olt gateway internal-base-url esta vacio`() {
        val client = HealthOltGatewayHttpClient(baseUrl = "", apiKey = "k")
        assertNull(client.findBySn("HWTC1"))
        assertNull(client.findByExternalId("ext"))
        assertEquals(emptyList<Any>(), client.findByOlt(1L))
        assertNull(client.findOltIdByName("olt"))
        assertEquals(false, client.refreshBySn("SN1").collected)
        assertEquals(emptyList<Any>(), client.pullOptical())
        assertEquals(emptyList<Any>(), client.pullStates())
    }
}
