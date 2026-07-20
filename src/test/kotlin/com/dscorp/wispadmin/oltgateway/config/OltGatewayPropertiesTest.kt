package com.dscorp.wispadmin.oltgateway.config

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@SpringBootTest(classes = [OltGatewayPropertiesTest.Config::class])
@TestPropertySource(
    properties = [
        "olt.gateway.enabled=true",
        "olt.gateway.sync.skip-when-write-running=true",
        "olt.gateway.sync.inventory-enabled=false",
        "olt.gateway.sync.signal-enabled=false"
    ]
)
class OltGatewayPropertiesTest {

    @EnableConfigurationProperties(OltGatewayProperties::class)
    class Config

    @Autowired
    private lateinit var properties: OltGatewayProperties

    @Test
    fun `sync nested properties estan inicializadas en el bean Spring`() {
        assertNotNull(properties.sync)
        assertTrue(properties.sync.skipWhenWriteRunning)
        assertNotNull(properties.session)
        assertNotNull(properties.inventory)
    }
}
