package com.dscorp.wispadmin.routeros

import com.dscorp.wispadmin.routeros.adapter.RouterOs7RestAdapter
import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import com.dscorp.wispadmin.routeros.port.MikrotikClient
import com.dscorp.wispadmin.routeros.port.MikrotikDeviceRef
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("live-mk1")
class RouterOs7RestAdapterTest : MikrotikClientContractTest() {

    override fun createClient(): MikrotikClient {
        val properties = RouterOsClientProperties().apply {
            adapter = "rest"
            rest.port = Mk1LiveSupport.restDevice().port
            rest.timeoutMs = 15000
            rest.verifySsl = System.getenv("ROUTEROS_MK1_VERIFY_SSL")?.toBoolean() ?: false
        }
        return RouterOs7RestAdapter(properties)
    }

    override fun validDevice(): MikrotikDeviceRef = Mk1LiveSupport.restDevice()

    override fun authFailDevice(): MikrotikDeviceRef = Mk1LiveSupport.authFailRestDevice()

    @Test
    fun `resource version contains 7_23 via REST`() {
        val client = createClient()
        val rows = client.withSession(validDevice()) { session ->
            session.print("/system/resource")
        }
        val version = rows.first()["version"].orEmpty()
        assertTrue(version.contains("7.23"), "expected ROS 7.23.x, got: $version")
    }
}
