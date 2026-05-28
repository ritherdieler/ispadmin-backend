package com.dscorp.wispadmin.wispadmin.requestbody

data class PaymentCreateRequest(
    val subscriptionId: Int,
    val amountPaid: Double,
    val discountAmount: Double?,
    val discountReason: String?,
    val method: String,
    val electronicPayerName: String?,
    val paymentDate: Long?,
    val billingDate: Long?,
    val responsibleId: Int
)
