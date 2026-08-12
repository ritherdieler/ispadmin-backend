package com.dscorp.wispadmin.wispadmin.requestbody

data class MultiPaymentRegisterRequest(
    val paymentIds: List<Int>,
    val method: String,
    val responsibleId: Int,
    val discountAmount: Double = 0.0,
    val discountReason: String? = null,
    val electronicPayerName: String? = null,
    val proofImagePath: String? = null,
    val inboundMessageId: Int? = null,
)
