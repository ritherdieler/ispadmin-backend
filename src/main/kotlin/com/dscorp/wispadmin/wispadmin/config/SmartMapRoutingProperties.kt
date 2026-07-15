package com.dscorp.wispadmin.wispadmin.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "smartmap.routing")
class SmartMapRoutingProperties {
    var provider: String = "osrm"
    var osrm: OsrmProperties = OsrmProperties()

    class OsrmProperties {
        var baseUrl: String = "https://router.project-osrm.org"
        var timeoutMs: Long = 20_000
        var maxChunkSize: Int = 18
        var maxConcurrent: Int = 2
        var batchDelayMs: Long = 350
        var maxRetries: Int = 2
        var maxRenderPoints: Int = 4_000
    }
}
