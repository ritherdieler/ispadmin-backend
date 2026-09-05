package com.dscorp.wispadmin.wispadmin.oltclient

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(OltGatewayClientProperties::class)
class OltGatewayClientPropertiesConfig

@ConfigurationProperties(prefix = "olt.gateway")
class OltGatewayClientProperties {
    var apiKey: String = ""
    var internalBaseUrl: String = ""
    var clientEnabled: Boolean = false
}
