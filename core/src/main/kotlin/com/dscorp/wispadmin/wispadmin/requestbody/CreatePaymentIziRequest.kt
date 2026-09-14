package com.dscorp.wispadmin.wispadmin.requestbody

data class CreatePaymentIziRequest(
    val amount: String,
    val currency: String,
    val customer: Customer,
    val formTokenVersion: Int,
    val mode: String,
    val orderId: String,
    val formAction:String? = null
)