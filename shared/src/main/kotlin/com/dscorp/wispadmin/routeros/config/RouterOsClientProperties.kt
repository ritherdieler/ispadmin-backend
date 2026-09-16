package com.dscorp.wispadmin.routeros.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "router.os.client")
class RouterOsClientProperties {

    @Deprecated("Classic RouterOS API is unused; REST is the only transport")
    var adapter: String = "rest"

    @Deprecated("Classic RouterOS API is unused; REST is the only transport")
    @Suppress("DEPRECATION")
    val classic: ClassicProperties = ClassicProperties()

    val rest: RestProperties = RestProperties()

    @Deprecated("Classic RouterOS API is unused; REST is the only transport")
    class ClassicProperties {
        var port: Int = 8728
        var timeoutMs: Long = 10000
    }

    class RestProperties {
        var scheme: String = "https"
        var port: Int = 443
        var verifySsl: Boolean = true
        var trustStore: String = ""
        var trustStorePassword: String = ""
        var timeoutMs: Long = 10000
    }
}
