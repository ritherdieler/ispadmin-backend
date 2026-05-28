package com.dscorp.wispadmin.wispadmin.requestbody

data class PaymentUpdateRequest(
    val amountToPay: Double?,
    val amountPaid: Double?,
    val discountAmount: Double?,
    val discountReason: String?,
    val method: String?,
    val electronicPayerName: String?,
    val paymentDate: Long?,
    val billingDate: Long?
)
