package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** FIBER registrations are owned by the durable v2 journal; promotion must share the subscription insert transaction. */
@Service
class FiberRegistrationService(private val registration: ProvisioningV2RegistrationService) {
    fun handles(request: SubscriptionRequest): Boolean = request.installationType == InstallationType.FIBER

    fun prepare(operatorId: Long?, request: SubscriptionRequest) {
        if (request.registrationOperationId.isNullOrBlank()) return
        registration.prepareSubmission(operatorId, request)
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun promote(subscription: Subscription, request: SubscriptionRequest, operatorId: Long?): ProvisioningOperation =
        registration.start(subscription, request, operatorId)
}
