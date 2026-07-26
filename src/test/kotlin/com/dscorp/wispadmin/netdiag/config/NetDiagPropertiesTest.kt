package com.dscorp.wispadmin.netdiag.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@SpringBootTest(classes = [NetDiagPropertiesTest.Config::class])
@TestPropertySource(
    properties = [
        "net.diag.enabled=true",
        "net.diag.api-key=dev-netdiag-key",
        "net.diag.poll.concurrency=4",
        "net.diag.poll.jitter-ms=5000",
        "net.diag.retention.probe-run-days=30",
        "net.diag.alert.cooldown-minutes=15",
        "net.diag.alert.min-duration-seconds=120"
    ]
)
class NetDiagPropertiesTest {

    @EnableConfigurationProperties(NetDiagProperties::class)
    class Config

    @Autowired
    private lateinit var properties: NetDiagProperties

    @Test
    fun `nested properties estan inicializadas en el bean Spring`() {
        assertTrue(properties.enabled)
        assertEquals("dev-netdiag-key", properties.apiKey)
        assertNotNull(properties.poll)
        assertEquals(4, properties.poll.concurrency)
        assertEquals(5000, properties.poll.jitterMs)
        assertNotNull(properties.retention)
        assertEquals(30, properties.retention.probeRunDays)
        assertNotNull(properties.alert)
        assertEquals(15, properties.alert.cooldownMinutes)
        assertEquals(120, properties.alert.minDurationSeconds)
    }

    @Test
    fun `isValidApiKey valida clave configurada`() {
        assertTrue(properties.isValidApiKey("dev-netdiag-key"))
        assertFalse(properties.isValidApiKey(null))
        assertFalse(properties.isValidApiKey(""))
        assertFalse(properties.isValidApiKey("wrong"))
    }
}
