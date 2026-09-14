package com.dscorp.wispadmin.acs.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "acs")
class AcsProperties {
    var apiKey: String = ""
    var genieacsToAcsApiKey: String = ""
    var enabled: Boolean = true
    val gateway: GatewayClientProperties = GatewayClientProperties()

    fun isValidApiKey(key: String?): Boolean {
        if (key.isNullOrBlank()) return false
        if (apiKey.isNotBlank() && apiKey == key) return true
        return false
    }

    fun isValidNotifyApiKey(key: String?): Boolean {
        if (key.isNullOrBlank()) return false
        if (apiKey.isNotBlank() && apiKey == key) return true
        if (genieacsToAcsApiKey.isNotBlank() && genieacsToAcsApiKey == key) return true
        return false
    }

    class GatewayClientProperties {
        var internalBaseUrl: String = ""
        var apiKey: String = ""
    }
}
