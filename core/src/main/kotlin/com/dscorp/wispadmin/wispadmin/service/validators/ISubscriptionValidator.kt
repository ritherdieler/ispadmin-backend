package com.dscorp.wispadmin.wispadmin.service.validators

import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest

interface ISubscriptionValidator {
    
    fun validateIp(
        ip: String?,
        subscriptionId: Int?,
        subscriptionName: String,
        prefix: String
    ): ValidationResult
    
    fun validateSubscriptionRequest(request: SubscriptionRequest)
}

data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)



