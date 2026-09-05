package com.dscorp.wispadmin.wispadmin.trafficclient

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(TrafficClientProperties::class)
class TrafficClientPropertiesConfig

@ConfigurationProperties(prefix = "traffic")
class TrafficClientProperties {
    var apiKey: String = ""
    var internalBaseUrl: String = ""
    var clientEnabled: Boolean = false

    fun isValidApiKey(key: String?): Boolean {
        if (apiKey.isBlank() || key.isNullOrBlank()) return false
        return apiKey == key
    }
}
