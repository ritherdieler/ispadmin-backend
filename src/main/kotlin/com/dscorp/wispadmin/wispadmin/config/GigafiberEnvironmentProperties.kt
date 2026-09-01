package com.dscorp.wispadmin.wispadmin.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "gigafiber.environment")
class GigafiberEnvironmentProperties {
    var tag: String = ""

    fun normalizedTag(): String {
        return tag.trim().lowercase()
    }
}
