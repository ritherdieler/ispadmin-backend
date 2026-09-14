package com.dscorp.wispadmin.traffic.client

import com.dscorp.wispadmin.traffic.config.TrafficProperties
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HttpTrafficDirectoryClientTest {

    @Test
    fun `list vacio si core-base-url esta en blanco`() {
        val client = HttpTrafficDirectoryClient(
            TrafficProperties().apply {
                coreBaseUrl = ""
                directoryTtlSeconds = 60
            },
            com.fasterxml.jackson.databind.ObjectMapper(),
        )
        assertTrue(client.list().isEmpty())
    }
}
