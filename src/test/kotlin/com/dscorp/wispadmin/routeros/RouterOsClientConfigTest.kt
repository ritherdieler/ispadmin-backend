package com.dscorp.wispadmin.routeros

import com.dscorp.wispadmin.routeros.config.RouterOsClientConfig
import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RouterOsClientConfigTest {

    @Test
    fun `warns when rest verify ssl is disabled`() {
        val properties = RouterOsClientProperties().apply {
            adapter = "rest"
            rest.verifySsl = false
        }
        val message = RouterOsClientConfig.sslVerifyDisabledWarning(properties)
        assertNotNull(message)
        assertTrue(message!!.contains("verify-ssl=false"))
    }

    @Test
    fun `no warning when rest verify ssl is enabled`() {
        val properties = RouterOsClientProperties().apply {
            adapter = "rest"
            rest.verifySsl = true
        }
        assertNull(RouterOsClientConfig.sslVerifyDisabledWarning(properties))
    }
}
