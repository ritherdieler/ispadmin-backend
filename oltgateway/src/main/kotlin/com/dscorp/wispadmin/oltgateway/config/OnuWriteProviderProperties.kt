package com.dscorp.wispadmin.oltgateway.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

enum class OnuWriteProvider {
    SMARTOLT,
    GATEWAY
}

@Configuration
@ConfigurationProperties(prefix = "olt.provider")
class OnuWriteProviderProperties {

    var authorize: OnuWriteProvider = OnuWriteProvider.GATEWAY
    var delete: OnuWriteProvider = OnuWriteProvider.GATEWAY
    var reboot: OnuWriteProvider = OnuWriteProvider.GATEWAY
    var move: OnuWriteProvider = OnuWriteProvider.GATEWAY
    var authorizeShadow: Boolean = false
}
