package com.dscorp.wispadmin.wispadmin.service.genieacs

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "genieacs")
class GenieAcsProperties {
    var enabled: Boolean = false
    var nbiBaseUrl: String = "http://127.0.0.1:7557"
    var waitTimeoutMs: Long = 90_000
    var offlineWaitTimeoutMs: Long = 15_000
    var pollIntervalMs: Long = 5_000
    var taskTimeoutMs: Long = 30_000
    var connectTimeoutMs: Long = 5_000
    var defaultDns: String = "8.8.8.8,8.8.4.4"
    var wanVlanId: Int = 1
    /** Emite cada POST NBI como curl + response HTTP en logs (INFO). Desactivar en prod normal. */
    var logCurl: Boolean = false
}
