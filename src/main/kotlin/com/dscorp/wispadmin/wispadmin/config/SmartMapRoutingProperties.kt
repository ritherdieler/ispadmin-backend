package com.dscorp.wispadmin.wispadmin.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "smartmap.routing")
class SmartMapRoutingProperties {
    var mapbox: MapboxProperties = MapboxProperties()

    class MapboxProperties {
        var accessToken: String = ""
        var baseUrl: String = "https://api.mapbox.com/directions/v5"
        var profile: String = "mapbox/driving"
        var language: String = "es"
        var timeoutMs: Long = 20_000
        var maxRetries: Int = 2
        var maxWaypoints: Int = 25
        var maxConcurrent: Int = 2
        var batchDelayMs: Long = 350
        var maxRenderPoints: Int = 4_000
    }
}
