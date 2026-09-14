package com.dscorp.wispadmin.wispadmin.requestbody

data class WhatsAppTemplateTestMessageRequest(
    val phoneNumber: String,
    val clientName: String,
    val amount: String,
    val billingPeriod: String
)