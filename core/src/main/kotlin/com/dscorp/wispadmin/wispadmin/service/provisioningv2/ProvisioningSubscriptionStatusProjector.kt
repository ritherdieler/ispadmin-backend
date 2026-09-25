package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.wispadmin.data.model.MikrotikProvisionStatus
import com.dscorp.wispadmin.wispadmin.data.model.OltProvisionStatus
import com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository

/** Mirrors the provisioning journal onto the subscription fields the register screen polls. */
class ProvisioningSubscriptionStatusProjector(
    private val journal: ProvisioningJournal,
    private val subscriptions: SubscriptionRepository,
) {
    fun project(environment: String, operationId: String) {
        val operation = journal.get(environment, operationId) ?: return
        val subscription = subscriptions.findById(operation.subscriptionId).orElse(null) ?: return
        fun stage(name: ProvisioningStage) = operation.checkpoints.first { it.stage == name }.state
        when (stage(ProvisioningStage.OLT)) {
            CheckpointState.SUCCEEDED -> subscription.oltProvisionStatus = OltProvisionStatus.COMPLETE
            CheckpointState.FAILED -> subscription.oltProvisionStatus = OltProvisionStatus.FAILED
            else -> Unit
        }
        when (stage(ProvisioningStage.MIKROTIK)) {
            CheckpointState.SUCCEEDED -> subscription.mikrotikProvisionStatus = MikrotikProvisionStatus.COMPLETE
            CheckpointState.FAILED -> subscription.mikrotikProvisionStatus = MikrotikProvisionStatus.FAILED
            else -> Unit
        }
        when (operation.state) {
            ProvisioningState.SUCCEEDED -> subscription.tr069ProvisionStatus = Tr069ProvisionStatus.COMPLETE
            ProvisioningState.FAILED -> subscription.tr069ProvisionStatus = Tr069ProvisionStatus.FAILED
            else -> Unit
        }
        subscriptions.save(subscription)
    }
}
