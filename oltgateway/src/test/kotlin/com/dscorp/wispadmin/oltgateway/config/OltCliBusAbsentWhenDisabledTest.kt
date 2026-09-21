package com.dscorp.wispadmin.oltgateway.config

import com.dscorp.wispadmin.oltgateway.ssh.OltCliBus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class OltCliBusAbsentWhenDisabledTest {

    @Test
    fun `olt gateway enabled false no crea OltCliBus`() {
        ApplicationContextRunner()
            .withUserConfiguration(OltGatewayConfig::class.java)
            .withPropertyValues("olt.gateway.enabled=false")
            .run { context ->
                assertEquals(0, context.getBeanNamesForType(OltCliBus::class.java).size)
            }
    }
}
