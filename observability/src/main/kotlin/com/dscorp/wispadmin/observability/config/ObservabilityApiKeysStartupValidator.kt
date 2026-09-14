package com.dscorp.wispadmin.observability.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class ObservabilityApiKeysStartupValidator(
    private val properties: ObservabilityProperties,
    @Value("\${spring.profiles.active:}") private val activeProfiles: String,
    private val logger: Logger = LoggerFactory.getLogger(ObservabilityApiKeysStartupValidator::class.java)
) {

    companion object {
        val REQUIRED_IN_PROD = listOf("android", "dashboard", "web-backoffice", "web-asistencias")
    }

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady(@Suppress("UNUSED_PARAMETER") event: ApplicationReadyEvent) {
        validateProdApiKeys()
    }

    fun validateProdApiKeys() {
        if (!isProdProfile()) return
        if (!properties.enabled) return
        val missing = missingRequiredKeys()
        if (missing.isEmpty()) return
        logger.error(
            "Observability deshabilitada para ingestión en prod: faltan API keys en env " +
                "(observability.api-keys.*): ${missing.joinToString()}"
        )
    }

    internal fun missingRequiredKeys(): List<String> =
        REQUIRED_IN_PROD.filter { key -> properties.apiKeys[key].isNullOrBlank() }

    private fun isProdProfile(): Boolean =
        activeProfiles.split(",").any { it.trim().equals("prod", ignoreCase = true) }
}
