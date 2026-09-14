package com.dscorp.wispadmin.routeros

import com.dscorp.wispadmin.routeros.adapter.RouterOs7RestAdapter
import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import com.dscorp.wispadmin.routeros.port.MikrotikDeviceRef
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Mk2RestConnectSmokeTest {

    @Test
    fun `connect MK2 queue print with truststore`() {
        val host = System.getenv("ROUTEROS_MK2_HOST") ?: "38.224.231.4"
        val user = System.getenv("ROUTEROS_MK2_USER") ?: return
        val password = System.getenv("ROUTEROS_MK2_PASSWORD") ?: return
        val properties = RouterOsClientProperties().apply {
            rest.port = 443
            rest.scheme = "https"
            rest.verifySsl = System.getenv("ROUTEROS_MK2_VERIFY_SSL")?.toBoolean() ?: true
            rest.trustStore = "classpath:routeros-mk-truststore.jks"
            rest.trustStorePassword = "changeit"
            rest.timeoutMs = 30000
        }
        val adapter = RouterOs7RestAdapter(properties)
        val device = MikrotikDeviceRef(
            id = "8",
            host = host,
            port = 8728,
            username = user,
            password = password
        )
        val started = System.nanoTime()
        val rows = adapter.withSession(device) { session ->
            session.print("/queue/simple", proplist = listOf(".id", "target", "bytes"))
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        println("MK2 queue print rows=${rows.size} elapsedMs=$elapsedMs verifySsl=${properties.rest.verifySsl}")
        adapter.close()
        assertTrue(rows.isNotEmpty(), "expected queues from MK2")
        assertTrue(elapsedMs < 25_000, "MK2 REST should respond within poll timeout, took ${elapsedMs}ms")
    }
}
