package com.dscorp.wispadmin.routeros

import com.dscorp.wispadmin.routeros.adapter.RouterOs7RestAdapter
import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import com.dscorp.wispadmin.routeros.port.MikrotikAuthException
import com.dscorp.wispadmin.routeros.port.MikrotikDeviceRef
import com.dscorp.wispadmin.routeros.port.MikrotikTimeoutException
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class RouterOs7RestAdapterUnitTest {

    private lateinit var server: MockWebServer
    private lateinit var adapter: RouterOs7RestAdapter

    private val device = MikrotikDeviceRef(
        id = "unit-rest",
        host = "127.0.0.1",
        port = 0,
        username = "admin",
        password = "secret"
    )

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val properties = RouterOsClientProperties().apply {
            adapter = "rest"
            rest.port = server.port
            rest.timeoutMs = 1000
            rest.verifySsl = false
            rest.scheme = "http"
        }
        adapter = RouterOs7RestAdapter(properties)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
        adapter.close()
    }

    @Test
    fun `print system identity maps to REST print path`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[{"name":"MK1-UNIT"}]""")
                .addHeader("Content-Type", "application/json")
        )

        val rows = adapter.withSession(device.copy(port = server.port)) { session ->
            session.print("/system/identity")
        }

        assertEquals("MK1-UNIT", rows.first()["name"])
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.path!!.endsWith("/rest/system/identity/print"))
    }

    @Test
    fun `print system resource returns version`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[{"version":"7.23.2 (stable)","uptime":"1d"}]""")
                .addHeader("Content-Type", "application/json")
        )

        val rows = adapter.withSession(device.copy(port = server.port)) { session ->
            session.print("/system/resource")
        }

        assertEquals("7.23.2 (stable)", rows.first()["version"])
    }

    @Test
    fun `empty JSON array returns empty list`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("[]")
                .addHeader("Content-Type", "application/json")
        )

        val rows = adapter.withSession(device.copy(port = server.port)) { session ->
            session.print("/system/identity", mapOf("name" to "missing"))
        }

        assertTrue(rows.isEmpty())
    }

    @Test
    fun `http 401 maps to MikrotikAuthException`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))

        assertThrows(MikrotikAuthException::class.java) {
            adapter.withSession(device.copy(port = server.port)) { session ->
                session.print("/system/identity")
            }
        }
    }

    @Test
    fun `socket timeout maps to MikrotikTimeoutException`() {
        server.enqueue(
            MockResponse()
                .setBody("""[{"name":"slow"}]""")
                .setBodyDelay(3, TimeUnit.SECONDS)
                .addHeader("Content-Type", "application/json")
        )

        assertThrows(MikrotikTimeoutException::class.java) {
            adapter.withSession(device.copy(port = server.port)) { session ->
                session.print("/system/identity")
            }
        }
    }

    @Test
    fun `add set remove map to REST verbs`() {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"ret":"*1"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        adapter.withSession(device.copy(port = server.port)) { session ->
            session.add("/ip/address", mapOf("address" to "10.0.0.1/24", "interface" to "ether1"))
            session.set("/ip/address", "*1", mapOf("comment" to "x"))
            session.remove("/ip/address", "*1")
        }

        val addReq = server.takeRequest()
        assertEquals("PUT", addReq.method)
        assertTrue(addReq.path!!.endsWith("/rest/ip/address"))

        val setReq = server.takeRequest()
        assertEquals("PATCH", setReq.method)
        assertTrue(setReq.path!!.contains("/rest/ip/address/*1"))

        val removeReq = server.takeRequest()
        assertEquals("DELETE", removeReq.method)
        assertTrue(removeReq.path!!.contains("/rest/ip/address/*1"))
    }
}
