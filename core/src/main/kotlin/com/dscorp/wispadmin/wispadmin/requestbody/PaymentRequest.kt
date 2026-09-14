package com.dscorp.wispadmin.wispadmin.requestbody

data class PaymentRequest(
    var id: Int? = null,
    var amountPaid: Double = 0.0,
    val discountAmount: Double = 0.0,
    val discountReason: String? = null,
    var method: String,
    var paid: Boolean = false,
    var subscriptionId: Int = -1,
    var responsibleId: Int,
    var electronicPayerName: String? = null,
    var billingDate: Long = System.currentTimeMillis(),
    var proofImagePath: String? = null,
    var inboundMessageId: Int? = null,
)