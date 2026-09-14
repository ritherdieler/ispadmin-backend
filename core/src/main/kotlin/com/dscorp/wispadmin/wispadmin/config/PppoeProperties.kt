package com.dscorp.wispadmin.wispadmin.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "pppoe")
class PppoeProperties {
    var newSubscriptions: NewSubscriptions = NewSubscriptions()
    var profile: ProfileSettings = ProfileSettings()
    var migration: MigrationSettings = MigrationSettings()

    class NewSubscriptions {
        var enabled: Boolean = false
    }

    class ProfileSettings {
        var remotePool: String = "PPPOE-DINAMICO"
        var localAddress: String = "10.64.0.1"
    }

    class MigrationSettings {
        var quarantineDays: Int = 7
    }
}
