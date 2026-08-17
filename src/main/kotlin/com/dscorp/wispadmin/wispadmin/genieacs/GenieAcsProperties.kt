package com.dscorp.wispadmin.wispadmin.genieacs

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "genieacs.nbi")
class GenieAcsProperties {
    var baseUrl: String = "http://127.0.0.1:7557"
    var username: String = ""
    var password: String = ""
    var timeoutMs: Long = 10_000
}
