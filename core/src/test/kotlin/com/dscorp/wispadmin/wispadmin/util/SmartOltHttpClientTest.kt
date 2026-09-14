package com.dscorp.wispadmin.wispadmin.util

import com.dscorp.wispadmin.wispadmin.config.OltServiceProperties
import com.dscorp.wispadmin.wispadmin.config.SmartOltHttpConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.client.ResourceAccessException
import java.util.concurrent.TimeUnit

class SmartOltHttpClientTest {

    private lateinit var server: MockWebServer
    private lateinit var properties: OltServiceProperties
    private lateinit var client: SmartOltHttpClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        properties = OltServiceProperties().apply {
            baseUrl = server.url("/api/").toString()
            apiKey = "test-token"
            connectTimeoutMs = 1_000
            readTimeoutMs = 1_000
        }
        client = SmartOltHttpClient(properties, SmartOltHttpConfig().smartOltRestTemplate(properties))
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `get resuelve la ruta sobre la base url y manda la api key de propiedades`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        client.get("onu/unconfigured_onus", String::class.java)

        val recorded = server.takeRequest()
        assertEquals("/api/onu/unconfigured_onus", recorded.path)
        assertEquals("test-token", recorded.getHeader("X-Token"))
    }

    @Test
    fun `post manda la api key de propiedades`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        client.post("onu/reboot/ext-1", null, String::class.java)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/onu/reboot/ext-1", recorded.path)
        assertEquals("test-token", recorded.getHeader("X-Token"))
    }

    @Test
    fun `una respuesta lenta corta por read timeout en vez de esperar sin limite`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{}")
                .setBodyDelay(5, TimeUnit.SECONDS)
        )

        val startedAt = System.currentTimeMillis()
        assertThrows<ResourceAccessException> {
            client.get("onu/unconfigured_onus", String::class.java)
        }
        val elapsed = System.currentTimeMillis() - startedAt

        assertTrue(elapsed < 4_000, "debia cortar por timeout, tardo ${elapsed}ms")
    }

    @Test
    fun `la api key no trae valor por defecto en codigo`() {
        assertEquals("", OltServiceProperties().apiKey)
    }
}
