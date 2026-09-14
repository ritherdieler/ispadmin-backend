package com.dscorp.wispadmin.wispadmin.acsclient

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(AcsClientProperties::class)
class AcsClientPropertiesConfig

@ConfigurationProperties(prefix = "acs")
class AcsClientProperties {
    var apiKey: String = ""
    var internalBaseUrl: String = ""
    var clientEnabled: Boolean = false
}
