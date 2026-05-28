package com.dscorp.wispadmin.wispadmin.requestbody

data class PaymentInvoiceCreateRequest(
    val amountToPay: Double,
    val subscriptionId: Int,
    val billingDate: Long
)
