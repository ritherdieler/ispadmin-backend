package com.dscorp.wispadmin.wispadmin.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "olt.service")
class OltServiceProperties {

    var baseUrl: String = "https://gigafiberperu.smartolt.com/api/"

    var apiKey: String = ""

    var connectTimeoutMs: Long = 5_000

    var readTimeoutMs: Long = 15_000

    val mock: MockProperties = MockProperties()

    class MockProperties {
        var enabled: Boolean = false
    }
}
