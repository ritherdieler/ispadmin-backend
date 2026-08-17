package com.dscorp.wispadmin.wispadmin.genieacs

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.reactive.function.client.WebClient
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class GenieAcsClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: GenieAcsClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val webClient = WebClient.builder()
            .baseUrl(server.url("/").toString().trimEnd('/'))
            .build()
        client = GenieAcsClient(webClient)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `updateWifi looks up device by serial and posts setParameterValues for TR-098 and TR-181`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    [{
                      "_id": "00259E-EG8145V5-HWTC15F5CD86",
                      "_lastInform": "2026-08-17T14:00:00.000Z"
                    }]
                    """.trimIndent()
                )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"_id":"task-1","name":"setParameterValues"}""")
        )

        client.updateWifi("HWTC15F5CD86", "GigaFiber-Casa", "clavewifi1")

        val lookup = server.takeRequest()
        assertEquals("GET", lookup.method)
        val decodedPath = URLDecoder.decode(lookup.path, StandardCharsets.UTF_8)
        assertTrue(decodedPath.contains("/devices/"))
        assertTrue(decodedPath.contains("""{"_deviceId._SerialNumber":"HWTC15F5CD86"}"""))

        val task = server.takeRequest()
        assertEquals("POST", task.method)
        assertTrue(task.path!!.contains("/devices/00259E-EG8145V5-HWTC15F5CD86/tasks"))
        val body = task.body.readUtf8()
        assertTrue(body.contains("setParameterValues"))
        assertTrue(body.contains("InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.SSID"))
        assertTrue(body.contains("InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.PreSharedKey.1.PreSharedKey"))
        assertTrue(body.contains("Device.WiFi.SSID.1.SSID"))
        assertTrue(body.contains("Device.WiFi.AccessPoint.1.Security.KeyPassphrase"))
        assertTrue(body.contains("GigaFiber-Casa"))
        assertTrue(body.contains("clavewifi1"))
    }

    @Test
    fun `updateWifi throws when GenieACS has no device for the serial`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("[]")
        )

        assertThrows<GenieAcsDeviceNotFoundException> {
            client.updateWifi("MISSINGSN", "Red", "password1")
        }
    }

    @Test
    fun `getLastInform returns ACS timestamp for the serial`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    [{
                      "_id": "00259E-EG8145V5-HWTC15F5CD86",
                      "_lastInform": "2026-08-17T14:00:00.000Z"
                    }]
                    """.trimIndent()
                )
        )

        val lastInform = client.getLastInform("HWTC15F5CD86")

        assertEquals("2026-08-17T14:00:00.000Z", lastInform)
    }

    @Test
    fun `getLastInform returns null when device is missing`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("[]")
        )

        assertNull(client.getLastInform("UNKNOWN"))
    }
}
