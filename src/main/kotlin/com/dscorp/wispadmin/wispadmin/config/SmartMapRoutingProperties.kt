package com.dscorp.wispadmin.wispadmin.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

@ConfigurationProperties(prefix = "smartmap.routing")
class SmartMapRoutingProperties {
    @NestedConfigurationProperty
    var mapbox: MapboxProperties = MapboxProperties()

    class MapboxProperties {
        var accessToken: String = ""
        var baseUrl: String = "https://api.mapbox.com/directions/v5"
        var matrixBaseUrl: String = "https://api.mapbox.com/directions-matrix/v1"
        var profile: String = "mapbox/driving-traffic"
        var language: String = "es"
        var voiceUnits: String = "metric"
        var approaches: String = "curb"
        var annotations: String = "duration,congestion"
        var avoidManeuverRadiusMeters: Int = 75
        var matrixMaxCoordinates: Int = 10
        var timeoutMs: Long = 20_000
        var maxRetries: Int = 2
        var maxWaypoints: Int = 25
        var maxConcurrent: Int = 2
        var batchDelayMs: Long = 350
        var maxRenderPoints: Int = 4_000
    }
}
