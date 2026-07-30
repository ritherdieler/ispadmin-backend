package com.dscorp.wispadmin.routeros

import com.dscorp.wispadmin.routeros.adapter.LegrangeClassicAdapter
import com.dscorp.wispadmin.routeros.adapter.RouterOs7RestAdapter
import com.dscorp.wispadmin.routeros.adapter.RouterOsRestClassicFallbackAdapter
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("live-mk1")
class RouterOsRestClassicFallbackAdapterLiveTest {

    @Test
    fun `lee identity via fallback cuando REST TLS falla en 443`() {
        Mk1LiveSupport.requireCredentials()
        val properties = Mk1LiveSupport.liveRestProperties().apply {
            classic.port = Mk1LiveSupport.classicDevice().port
            classic.timeoutMs = 15000
        }
        @Suppress("DEPRECATION")
        val adapter = RouterOsRestClassicFallbackAdapter(
            primary = RouterOs7RestAdapter(properties),
            fallback = LegrangeClassicAdapter(properties)
        )
        val device = Mk1LiveSupport.restDevice()

        val rows = adapter.withSession(device) { session ->
            session.print("/system/identity")
        }

        assertFalse(rows.isEmpty())
        assertFalse(rows.first()["name"].orEmpty().isBlank())
        adapter.close()
    }
}
