package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.wispadmin.data.model.AccessMode
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.mikrotik.PppoeProfileCatalog

/** Validates the durable subscription identity before any remote v2 effect is attempted. */
class ValidateProvisioningStageHandler(
    private val subscriptions: SubscriptionRepository,
) : ProvisioningStageHandler {
    override val stage = ProvisioningStage.VALIDATE

    override fun reconcile(context: ProvisioningStageContext): StageObservation {
        validate(context.operation)
        return StageObservation.SATISFIED
    }

    override fun apply(context: ProvisioningStageContext): StageObservation = reconcile(context)

    override fun compensate(context: ProvisioningStageContext): StageObservation = StageObservation.SATISFIED

    private fun validate(operation: ProvisioningOperation) {
        val subscription = subscriptions.lockIdentityOwner(operation.subscriptionId)
            ?: failure("SUBSCRIPTION_NOT_FOUND")
        if (!subscription.fiberOnuSn.equals(operation.serial, ignoreCase = true)) failure("ONU_IDENTITY_MISMATCH")
        if (subscription.accessMode != AccessMode.PPPOE_DYNAMIC) failure("PPPOE_DYNAMIC_REQUIRED")
        if (subscription.pppoeUsername.isNullOrBlank()) failure("PPPOE_USERNAME_REQUIRED")
        if (subscription.pppoePasswordEnc.isNullOrBlank()) failure("PPPOE_PASSWORD_REQUIRED")
        if (PppoeProfileCatalog.profileName(subscription.plan) == null) failure("PPPOE_PROFILE_REQUIRED")
        if (subscription.hostDevice?.disabled != false) failure("MIKROTIK_UNAVAILABLE", retryable = true)
    }

    private fun failure(code: String, retryable: Boolean = false): Nothing = throw ProvisioningStepException(
        ProvisioningFailure(code, "La suscripción no cumple las precondiciones del registro v2.", retryable))
}
