package com.dscorp.wispadmin.wispadmin.config

class WhatsAppRetentionProperties {
    var enabled: Boolean = true
    var paymentProofDays: Long = 730
    var generalInboundDays: Long = 90
    var unresolvedInboundMaxDays: Long = 365
    var outboundDays: Long = 60
    var outboundFailedDays: Long = 30
    var minAgeDays: Long = 7
}
