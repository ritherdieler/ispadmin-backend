package com.dscorp.wispadmin.routeros

import com.dscorp.wispadmin.routeros.adapter.RouterOs7RestAdapter
import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import com.dscorp.wispadmin.routeros.port.MikrotikDeviceRef
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RouterOs7RestAdapterPortResolutionTest {

    private lateinit var restServer: MockWebServer

    @BeforeEach
    fun setUp() {
        restServer = MockWebServer()
        restServer.start()
    }

    @AfterEach
    fun tearDown() {
        restServer.shutdown()
    }

    @Test
    fun `uses rest port when device ref omits port`() {
        val properties = RouterOsClientProperties().apply {
            rest.port = restServer.port
            rest.scheme = "http"
            rest.verifySsl = false
            rest.timeoutMs = 3000
        }
        val adapter = RouterOs7RestAdapter(properties)
        restServer.enqueue(
            MockResponse()
                .setBody("""[{"name":"lab"}]""")
                .addHeader("Content-Type", "application/json")
        )

        val device = MikrotikDeviceRef(
            id = "prod-like",
            host = "127.0.0.1",
            port = 0,
            username = "admin",
            password = "secret"
        )

        adapter.withSession(device) { session ->
            session.print("/system/identity")
        }

        val recorded = restServer.takeRequest()
        assertEquals(restServer.port, recorded.requestUrl?.port)
    }

    @Test
    fun resolveRestPort_mapsMissingPortToRestPort() {
        val properties = RouterOsClientProperties().apply {
            rest.port = 443
        }
        val device = MikrotikDeviceRef(
            id = "x",
            host = "10.0.0.1",
            port = 0,
            username = "u",
            password = "p"
        )
        assertEquals(443, RouterOs7RestAdapter.resolveRestPort(device, properties))
    }

    @Test
    @Suppress("DEPRECATION")
    fun resolveRestPort_mapsClassicApiPortToRestPort() {
        val properties = RouterOsClientProperties().apply {
            classic.port = 8728
            rest.port = 443
        }
        val device = MikrotikDeviceRef(
            id = "x",
            host = "10.0.0.1",
            port = 8728,
            username = "u",
            password = "p"
        )
        assertEquals(443, RouterOs7RestAdapter.resolveRestPort(device, properties))
    }

    @Test
    fun resolveRestPort_keepsExplicitRestPort() {
        val properties = RouterOsClientProperties().apply {
            rest.port = 443
        }
        val device = MikrotikDeviceRef(
            id = "x",
            host = "10.0.0.1",
            port = 8443,
            username = "u",
            password = "p"
        )
        assertEquals(8443, RouterOs7RestAdapter.resolveRestPort(device, properties))
    }
}
