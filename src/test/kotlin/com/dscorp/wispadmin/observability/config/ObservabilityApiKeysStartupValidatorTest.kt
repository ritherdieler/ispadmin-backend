package com.dscorp.wispadmin.observability.config

import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.Logger

class ObservabilityApiKeysStartupValidatorTest {

    @Test
    fun `missingRequiredKeys incluye android cuando esta vacia`() {
        val properties = ObservabilityProperties().apply {
            apiKeys["android"] = ""
            apiKeys["dashboard"] = "dash-key"
            apiKeys["web-backoffice"] = "bo"
            apiKeys["web-asistencias"] = "as"
        }
        val validator = ObservabilityApiKeysStartupValidator(properties, "prod", mockk(relaxed = true))

        assertTrue(validator.missingRequiredKeys().contains("android"))
    }

    @Test
    fun `onApplicationReady registra error en prod cuando falta android`() {
        val properties = ObservabilityProperties().apply {
            apiKeys["android"] = ""
            apiKeys["dashboard"] = "dash-key"
            apiKeys["web-backoffice"] = "bo"
            apiKeys["web-asistencias"] = "as"
        }
        val logger = mockk<Logger>(relaxed = true)
        val validator = ObservabilityApiKeysStartupValidator(properties, "prod", logger)

        validator.validateProdApiKeys()

        verify {
            logger.error(match { it.contains("android") })
        }
    }

    @Test
    fun `missingRequiredKeys lista todas las claves vacias por defecto`() {
        val properties = ObservabilityProperties()
        val validator = ObservabilityApiKeysStartupValidator(properties, "dev", mockk(relaxed = true))

        assertEquals(ObservabilityApiKeysStartupValidator.REQUIRED_IN_PROD, validator.missingRequiredKeys())
    }
}
