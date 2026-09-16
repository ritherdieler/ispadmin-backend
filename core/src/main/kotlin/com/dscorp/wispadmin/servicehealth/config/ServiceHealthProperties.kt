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
    var sharedIncidentNotificationsEnabled = false
    var actionsEnabled = false
    var configEnabled = false
    var stationHmacKey = ""
    var periodicInformSeconds = 1800L
    var labPeriodicInformSeconds = 30L
    var snapshotFreshSeconds = 60L
    var opticalFreshSeconds = 2400L
    var stateFreshSeconds = 1200L
    var opticalDegradationDb = 3.0
    var opticalMinSamples = 12
    var opticalMinFlaps = 2
    var wifiRssiThreshold = -75.0
    var wifiSnrThreshold = 20.0
    var opticalRetentionDays = 90L
    var opticalDailyRetentionDays = 730L
    var opticalSeriesRawMaxDays = 90L
    var countRetentionDays = 90L
    var stationRetentionDays = 14L
    var stationHourlyRetentionDays = 90L
    var stationSeriesRawMaxDays = 7L
    var runRetentionDays = 30L
    var eventRetentionDays = 180L
    var actionCooldownSeconds = 600L
    var acsWifiSampleTargetSeconds = 1800L
    var opticalPullEnabled = false
    fun wifiSampleFreshSeconds() = acsWifiSampleTargetSeconds.coerceAtLeast(1) * 2
    var crConcurrency = 3
}
