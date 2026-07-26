package com.dscorp.wispadmin.netdiag.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "net.diag")
class NetDiagProperties {

    var enabled: Boolean = false

    var apiKey: String = ""

    val poll: PollProperties = PollProperties()

    val retention: RetentionProperties = RetentionProperties()

    val alert: AlertProperties = AlertProperties()

    val whatsapp: WhatsAppProperties = WhatsAppProperties()

    class PollProperties {
        var concurrency: Int = 4
        var jitterMs: Int = 5000
        var intervalMs: Long = 60000
        var initialDelayMs: Long = 15000
    }

    class RetentionProperties {
        var probeRunDays: Int = 30
    }

    class AlertProperties {
        var cooldownMinutes: Int = 15
        var minDurationSeconds: Int = 120
    }

    class WhatsAppProperties {
        var nocPhone: String = ""
        var templateName: String = "noc_alert_v1"
    }

    fun isValidApiKey(key: String?): Boolean {
        if (key.isNullOrBlank() || apiKey.isBlank()) return false
        return apiKey == key
    }
}
