package com.dscorp.wispadmin.acs.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "acs")
class AcsProperties {
    var apiKey: String = ""
    var enabled: Boolean = true

    fun isValidApiKey(key: String?): Boolean {
        if (apiKey.isBlank() || key.isNullOrBlank()) return false
        return apiKey == key
    }
}
