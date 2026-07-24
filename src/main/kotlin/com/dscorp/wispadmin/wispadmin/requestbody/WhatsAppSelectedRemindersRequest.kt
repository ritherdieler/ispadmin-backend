package com.dscorp.wispadmin.wispadmin.requestbody

data class WhatsAppSelectedRemindersRequest(
    val paymentIds: List<Int> = emptyList()
)
