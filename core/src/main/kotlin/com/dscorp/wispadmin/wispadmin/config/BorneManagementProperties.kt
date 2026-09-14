package com.dscorp.wispadmin.wispadmin.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "subscription.borne")
class BorneManagementProperties {
    var capacityCheckEnabled: Boolean = true
}
