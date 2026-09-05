package com.dscorp.wispadmin.wispadmin.oltclient

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.client.SimpleClientHttpRequestFactory
import java.time.Duration

class OltGatewayClientConfigTest {

    @Test
    fun `core to gateway read timeout covers olt authorize ssh`() {
        val factory = OltGatewayClientConfig().oltGatewayRestTemplate().requestFactory
            as SimpleClientHttpRequestFactory
        val readMs = readTimeoutMs(factory)
        assertTrue(
            readMs >= OltGatewayClientConfig.READ_TIMEOUT_MS,
            "activate waits on OLT SSH authorize (~30-180s); got ${readMs}ms",
        )
        assertTrue(OltGatewayClientConfig.READ_TIMEOUT_MS >= 180_000)
    }

    private fun readTimeoutMs(factory: SimpleClientHttpRequestFactory): Long {
        val field = SimpleClientHttpRequestFactory::class.java.getDeclaredField("readTimeout")
        field.isAccessible = true
        return when (val value = field.get(factory)) {
            is Int -> value.toLong()
            is Long -> value
            is Duration -> value.toMillis()
            else -> error("unexpected readTimeout type ${value?.javaClass?.name}: $value")
        }
    }
}
