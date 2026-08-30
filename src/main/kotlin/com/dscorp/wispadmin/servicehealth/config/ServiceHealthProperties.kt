package com.dscorp.wispadmin.servicehealth.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("service.health")
class ServiceHealthProperties {
    var enabled = false
    var opticalEnabled = false
    var acsEnabled = false
    var correlationEnabled = false
    var sharedIncidentsEnabled = false
    var actionsEnabled = false
    var configEnabled = false
    // An empty allowlist intentionally collects nothing. Reads remain available.
    var pilotAcsDeviceIds: List<String> = emptyList()
    var pilotSubscriptionIds: Set<Int> = emptySet()
    var stationHmacKey = ""
    var periodicInformSeconds = 3600L
    var opticalFreshSeconds = 600L
    var stateFreshSeconds = 1200L
    var opticalDegradationDb = 3.0
    var opticalMinSamples = 12
    var opticalMinFlaps = 2
    var wifiRssiThreshold = -75.0
    var wifiSnrThreshold = 20.0
    var opticalRetentionDays = 90L
    var countRetentionDays = 90L
    var stationRetentionDays = 14L
    var runRetentionDays = 30L
    var eventRetentionDays = 180L
    var actionCooldownSeconds = 600L
    var crConcurrency = 3
    fun collects(id: Int?) = enabled && id != null && id in pilotSubscriptionIds
}
