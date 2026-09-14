package com.dscorp.wispadmin.wispadmin.requestbody

data class PaymentValidationResponse(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>
)
