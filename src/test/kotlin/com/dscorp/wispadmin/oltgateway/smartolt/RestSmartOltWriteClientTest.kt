package com.dscorp.wispadmin.oltgateway.smartolt

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RestSmartOltWriteClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: RestSmartOltWriteClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = RestSmartOltWriteClient(
            baseUrl = server.url("/api/").toString(),
            apiKey = "test-token",
            connectTimeoutMs = 1_000,
            readTimeoutMs = 1_000,
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `authorize posts form fields with X-Token`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status":true,"unique_external_id":"cloud_1_6_16"}""")
        )

        val result = client.authorize(
            SmartOltAuthorizeCommand(
                oltId = "gigafiber-ma5608t",
                ponType = "gpon",
                board = "1",
                port = "6",
                sn = "ZTEGDC47BFFD",
                vlan = "100",
                onuType = "F6600RV9.0.21",
                zone = "Zone 1",
                name = "lab",
                onuMode = "Routing",
                customProfile = "Generic_1",
            )
        )

        assertEquals("cloud_1_6_16", result.uniqueExternalId)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/onu/authorize_onu", recorded.path)
        assertEquals("test-token", recorded.getHeader("X-Token"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("sn=ZTEGDC47BFFD"), body)
        assertTrue(body.contains("board=1"), body)
        assertTrue(body.contains("port=6"), body)
    }

    @Test
    fun `delete and reboot post to cloud paths with X-Token`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":true}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":true}"""))

        client.delete("ext-1")
        client.reboot("ext-1")

        val delete = server.takeRequest()
        assertEquals("/api/onu/delete/ext-1", delete.path)
        assertEquals("test-token", delete.getHeader("X-Token"))
        val reboot = server.takeRequest()
        assertEquals("/api/onu/reboot/ext-1", reboot.path)
        assertEquals("POST", reboot.method)
    }

    @Test
    fun `move posts destination form to cloud`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":true,"unique_external_id":"ext-1"}"""))

        val result = client.move(
            "ZTEGDC47BFFD",
            SmartOltMoveCommand(oltId = "gigafiber-ma5608t", board = "1", port = "7"),
        )

        assertEquals("ext-1", result.uniqueExternalId)
        val recorded = server.takeRequest()
        assertEquals("/api/onu/move/ZTEGDC47BFFD", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("board=1"), body)
        assertTrue(body.contains("port=7"), body)
    }

    @Test
    fun `failed cloud status throws`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":false,"error":"busy"}"""))

        val ex = assertThrows<IllegalStateException> {
            client.delete("ext-1")
        }
        assertEquals("busy", ex.message)
    }
}
