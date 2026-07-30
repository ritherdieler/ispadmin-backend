package com.dscorp.wispadmin.routeros

import com.dscorp.wispadmin.routeros.adapter.RouterOs7RestAdapter
import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import com.dscorp.wispadmin.routeros.port.MikrotikDeviceRef
import org.junit.jupiter.api.Assumptions.assumeTrue

object Mk1LiveSupport {

    private const val DEFAULT_HOST = "38.224.231.2"

    fun requireCredentials() {
        assumeTrue(
            !System.getenv("ROUTEROS_MK1_USER").isNullOrBlank(),
            "ROUTEROS_MK1_USER is required for live-mk1 tests"
        )
        assumeTrue(
            !System.getenv("ROUTEROS_MK1_PASSWORD").isNullOrBlank(),
            "ROUTEROS_MK1_PASSWORD is required for live-mk1 tests"
        )
    }

    fun classicDevice(): MikrotikDeviceRef {
        requireCredentials()
        return MikrotikDeviceRef(
            id = "mk1-classic",
            host = envOrDefault("ROUTEROS_MK1_HOST", DEFAULT_HOST),
            port = envOrDefault("ROUTEROS_MK1_CLASSIC_PORT", "8728").toInt(),
            username = System.getenv("ROUTEROS_MK1_USER")!!,
            password = System.getenv("ROUTEROS_MK1_PASSWORD")!!
        )
    }

    fun restDevice(): MikrotikDeviceRef {
        requireCredentials()
        return MikrotikDeviceRef(
            id = "mk1-rest",
            host = envOrDefault("ROUTEROS_MK1_HOST", DEFAULT_HOST),
            port = envOrDefault("ROUTEROS_MK1_REST_PORT", "443").toInt(),
            username = System.getenv("ROUTEROS_MK1_USER")!!,
            password = System.getenv("ROUTEROS_MK1_PASSWORD")!!
        )
    }

    fun authFailClassicDevice(): MikrotikDeviceRef {
        val base = classicDevice()
        return base.copy(username = "netdiag-invalid-user", password = "netdiag-invalid-pass")
    }

    fun authFailRestDevice(): MikrotikDeviceRef {
        val base = restDevice()
        return base.copy(username = "netdiag-invalid-user", password = "netdiag-invalid-pass")
    }

    fun liveRestProperties(timeoutMs: Long = 15000): RouterOsClientProperties {
        return RouterOsClientProperties().apply {
            rest.port = restDevice().port
            rest.verifySsl = System.getenv("ROUTEROS_MK1_VERIFY_SSL")?.toBoolean() ?: true
            rest.trustStore = "classpath:routeros-mk-truststore.jks"
            rest.trustStorePassword = envOrDefault("ROUTEROS_MK1_TRUSTSTORE_PASSWORD", "changeit")
            rest.timeoutMs = timeoutMs
        }
    }

    fun assumeRestAvailable() {
        requireCredentials()
        if (System.getenv("ROUTEROS_MK1_SKIP_REST")?.equals("true", ignoreCase = true) == true) {
            assumeTrue(false, "ROUTEROS_MK1_SKIP_REST=true")
        }
        val device = restDevice()
        val adapter = RouterOs7RestAdapter(liveRestProperties(timeoutMs = 5000))
        try {
            adapter.use {
                it.withSession(device) { session ->
                    session.print("/system/identity")
                }
            }
        } catch (error: Exception) {
            assumeTrue(
                false,
                "MK1 REST (${device.host}:${device.port}) unavailable: ${error.message}. " +
                    "Configure www-ssl certificate or set ROUTEROS_MK1_SKIP_REST=true."
            )
        }
    }

    private fun envOrDefault(name: String, default: String): String {
        val value = System.getenv(name)
        return if (value.isNullOrBlank()) default else value
    }
}
