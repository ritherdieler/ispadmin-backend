package com.dscorp.wispadmin.wispadmin.service.subscription

interface IServiceReactivationManager {
    
    fun reactivateService(
        subscriptionId: Int,
        responsibleId: Int,
        notes: String? = null,
        newBorneNumber: String? = null
    )
    
    fun registerPaymentCommitment(subscriptionId: Int)
}



