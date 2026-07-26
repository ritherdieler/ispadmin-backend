package com.dscorp.wispadmin.routeros.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "router.os.client")
class RouterOsClientProperties {

    var adapter: String = "classic"

    val classic: ClassicProperties = ClassicProperties()

    val rest: RestProperties = RestProperties()

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
