package com.dscorp.wispadmin.acs.genieacs

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
    /** WANConnectionDevice de fábrica (TR-069/staging). El alta FIBER no lo modifica. */
    var stagingWanIndex: Int = 1
    /** WANConnectionDevice del Internet del abonado (Static prod). */
    var clientWanIndex: Int = 2
    /** Nombre TR-069 de la WAN cliente. `{vlan}` se sustituye por `subscription.vlan`. */
    var clientWanNamePattern: String = "2_INTERNET_R_VID_{vlan}"
    /** Emite cada POST NBI como curl + response HTTP en logs (INFO). Desactivar en prod normal. */
    var logCurl: Boolean = false
}
