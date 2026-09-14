package com.dscorp.wispadmin.servicehealth.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HealthTrafficHttpClientTest {

    @Test
    fun `degrada a null si traffic internal-base-url esta vacio`() {
        val client = HealthTrafficHttpClient(baseUrl = "", apiKey = "k")
        assertNull(client.latestSample(2360))
        assertNull(client.latestRun(8))
        assertEquals(emptyList<Any>(), client.findAnomalyChanges(java.time.LocalDateTime.now(), 0, org.springframework.data.domain.PageRequest.of(0, 10)))
        assertEquals(80.0, client.minimumCoveragePct())
    }
}
