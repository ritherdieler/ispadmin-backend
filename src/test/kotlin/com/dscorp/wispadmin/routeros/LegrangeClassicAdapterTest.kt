package com.dscorp.wispadmin.routeros

import com.dscorp.wispadmin.routeros.adapter.LegrangeClassicAdapter
import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import com.dscorp.wispadmin.routeros.port.MikrotikClient
import com.dscorp.wispadmin.routeros.port.MikrotikDeviceRef
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("live-mk1")
class LegrangeClassicAdapterTest : MikrotikClientContractTest() {

    override fun createClient(): MikrotikClient {
        val properties = RouterOsClientProperties().apply {
            classic.port = Mk1LiveSupport.classicDevice().port
            classic.timeoutMs = 15000
        }
        return LegrangeClassicAdapter(properties)
    }

    override fun validDevice(): MikrotikDeviceRef = Mk1LiveSupport.classicDevice()

    override fun authFailDevice(): MikrotikDeviceRef = Mk1LiveSupport.authFailClassicDevice()

    @Test
    fun `resource version contains 7_23`() {
        val client = createClient()
        val rows = client.withSession(validDevice()) { session ->
            session.print("/system/resource")
        }
        val version = rows.first()["version"].orEmpty()
        assertTrue(version.contains("7.23"), "expected ROS 7.23.x, got: $version")
    }
}
