package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import java.time.Instant

enum class ProvisioningStage { VALIDATE, MIKROTIK, OLT, OMCI, ACS_CONTACT, INTERNET, WIFI, WAN_CLEANUP, VERIFY }
enum class ManagementProvisioningMode { OMCI, PRECONFIGURED }
enum class ProvisioningPhase { OLT_AUTHORIZATION, WAITING_FOR_ACS, READY_FOR_FORM, PROVISIONING }
enum class ProvisioningState { PENDING, RUNNING, WAITING, READY_FOR_FORM, SUCCEEDED, FAILED, CANCEL_REQUESTED, CANCELLING, CANCEL_FAILED, CANCELLED }
enum class CheckpointState { PENDING, RUNNING, WAITING, SUCCEEDED, FAILED, COMPENSATED }

data class ProvisioningFailure(
    val code: String,
    val message: String,
    val retryable: Boolean,
    val technicalDetails: String? = null,
)
data class StageCheckpoint(
    val stage: ProvisioningStage,
    val state: CheckpointState = CheckpointState.PENDING,
    val attempts: Int = 0,
    val touched: Boolean = false,
    val failure: ProvisioningFailure? = null,
)

data class ProvisioningOperation(
    val id: String,
    val environment: String,
    val subscriptionId: Int?,
    val serial: String,
    val revision: Long = 1,
    val flowVersion: Int = 2,
    val managementMode: ManagementProvisioningMode = ManagementProvisioningMode.OMCI,
    val phase: ProvisioningPhase = ProvisioningPhase.PROVISIONING,
    val operatorId: Long? = null,
    val operatorUsername: String? = null,
    val registrationRequestKey: String? = null,
    val onuTarget: ProvisioningOnuTarget? = null,
    val oltEvidence: OltProvisioningResource? = null,
    val acsContactEvidence: AcsContactProvisioningResource? = null,
    val operationFailure: ProvisioningFailure? = null,
    val state: ProvisioningState = ProvisioningState.PENDING,
    val checkpoints: List<StageCheckpoint> = ProvisioningStage.values().map { StageCheckpoint(it) },
    val updatedAt: Instant = Instant.EPOCH,
) {
    init {
        require(flowVersion in 2..3)
        require(id.isNotBlank() && environment.isNotBlank() && serial.isNotBlank())
        require(subscriptionId == null || subscriptionId > 0)
        require(phase != ProvisioningPhase.PROVISIONING || subscriptionId != null)
        require(phase == ProvisioningPhase.PROVISIONING || flowVersion == 3)
        require(checkpoints.map { it.stage } == ProvisioningStage.values().toList())
    }
}

data class ProvisioningOnuTarget(
    val oltId: String,
    val ponType: String,
    val board: String,
    val port: String,
    val onuType: String,
    val vlan: Int,
    val zone: String = "Zone 1",
    val onuMode: String = "Routing",
    val customProfile: String = "Generic_1",
)

/** Pure transitions only; the durable executor owns leases and external effects. */
class ProvisioningTransitions {
    private val compensationOrder = listOf(ProvisioningStage.VERIFY, ProvisioningStage.WAN_CLEANUP, ProvisioningStage.WIFI,
        ProvisioningStage.INTERNET, ProvisioningStage.ACS_CONTACT, ProvisioningStage.MIKROTIK,
        ProvisioningStage.OMCI, ProvisioningStage.OLT, ProvisioningStage.VALIDATE)
    private val cancelling = setOf(ProvisioningState.CANCEL_REQUESTED, ProvisioningState.CANCELLING,
        ProvisioningState.CANCEL_FAILED, ProvisioningState.CANCELLED)

    fun retry(operation: ProvisioningOperation, expectedRevision: Long): ProvisioningOperation {
        require(operation.revision == expectedRevision) { "STALE_REVISION" }
        check(operation.state !in cancelling) { "CANCELLATION_IN_PROGRESS" }
        if (operation.state == ProvisioningState.WAITING && operation.phase == ProvisioningPhase.WAITING_FOR_ACS) {
            val waiting = operation.checkpoints.singleOrNull { it.state == CheckpointState.WAITING }
            if (waiting?.stage != ProvisioningStage.ACS_CONTACT) return operation
            return update(operation, waiting.stage, ProvisioningState.PENDING) {
                it.copy(state = CheckpointState.PENDING, failure = null)
            }
        }
        if (operation.state != ProvisioningState.FAILED) return operation
        check(operation.checkpoints.none { it.failure?.retryable == false }) { "CORRECTION_REQUIRED" }
        return operation.copy(state = ProvisioningState.PENDING, checkpoints = operation.checkpoints.map {
            if (it.state == CheckpointState.FAILED) it.copy(state = CheckpointState.PENDING, failure = null) else it
        })
    }

    fun cancel(operation: ProvisioningOperation, expectedRevision: Long): ProvisioningOperation {
        require(operation.revision == expectedRevision) { "STALE_REVISION" }
        check(operation.state != ProvisioningState.SUCCEEDED) { "USE_SUBSCRIPTION_TERMINATION" }
        if (operation.state in cancelling && operation.state != ProvisioningState.CANCEL_FAILED) return operation
        return operation.copy(state = ProvisioningState.CANCEL_REQUESTED, operationFailure = null)
    }

    fun cancelRegistration(operation: ProvisioningOperation, expectedRevision: Long): ProvisioningOperation {
        require(operation.revision == expectedRevision) { "STALE_REVISION" }
        require(operation.flowVersion == 3 && operation.operatorId != null) { "REGISTRATION_CANCELLATION_REQUIRED" }
        if (operation.state == ProvisioningState.SUCCEEDED) {
            return operation.copy(state = ProvisioningState.CANCEL_REQUESTED, operationFailure = null)
        }
        return cancel(operation, expectedRevision)
    }

    fun next(operation: ProvisioningOperation): ProvisioningStage? {
        if (operation.phase != ProvisioningPhase.PROVISIONING || operation.state in cancelling ||
            operation.state in setOf(ProvisioningState.FAILED, ProvisioningState.SUCCEEDED, ProvisioningState.READY_FOR_FORM)) return null
        return operation.checkpoints.firstOrNull { it.state != CheckpointState.SUCCEEDED }?.stage
    }

    fun nextCompensation(operation: ProvisioningOperation): ProvisioningStage? {
        if (operation.state !in cancelling || operation.state == ProvisioningState.CANCELLED) return null
        return compensationOrder.firstOrNull { stage ->
            operation.checkpoints[stage.ordinal].let { it.touched && it.state != CheckpointState.COMPENSATED }
        }
    }

    fun started(operation: ProvisioningOperation, stage: ProvisioningStage): ProvisioningOperation {
        check(operation.state !in cancelling) { "CANCELLATION_IN_PROGRESS" }
        require(next(operation) == stage) { "INVALID_STAGE_ORDER" }
        return update(operation, stage, ProvisioningState.RUNNING) {
            it.copy(state = CheckpointState.RUNNING, attempts = it.attempts + 1, touched = true, failure = null)
        }
    }

    fun finished(operation: ProvisioningOperation, stage: ProvisioningStage): ProvisioningOperation {
        check(operation.state !in cancelling) { "CANCELLATION_IN_PROGRESS" }
        require(next(operation) == stage && operation.checkpoints[stage.ordinal].state == CheckpointState.RUNNING)
        val changed = update(operation, stage, ProvisioningState.PENDING) { it.copy(state = CheckpointState.SUCCEEDED, failure = null) }
        return if (changed.checkpoints.all { it.state == CheckpointState.SUCCEEDED }) changed.copy(state = ProvisioningState.SUCCEEDED) else changed
    }

    fun failed(operation: ProvisioningOperation, stage: ProvisioningStage, failure: ProvisioningFailure): ProvisioningOperation {
        require(if (operation.state in cancelling) nextCompensation(operation) == stage else next(operation) == stage)
        return update(operation, stage, if (operation.state in cancelling) ProvisioningState.CANCEL_FAILED else ProvisioningState.FAILED) {
            it.copy(state = CheckpointState.FAILED, failure = failure)
        }
    }

    fun compensated(operation: ProvisioningOperation, stage: ProvisioningStage): ProvisioningOperation {
        require(nextCompensation(operation) == stage) { "INVALID_COMPENSATION_ORDER" }
        val changed = update(operation, stage, ProvisioningState.CANCELLING) { it.copy(state = CheckpointState.COMPENSATED, failure = null) }
        return changed
    }

    fun waiting(operation: ProvisioningOperation, stage: ProvisioningStage): ProvisioningOperation {
        require(next(operation) == stage && operation.checkpoints[stage.ordinal].state == CheckpointState.RUNNING)
        return update(operation, stage, ProvisioningState.WAITING) { it.copy(state = CheckpointState.WAITING) }
    }

    private fun update(operation: ProvisioningOperation, stage: ProvisioningStage, state: ProvisioningState,
        transform: (StageCheckpoint) -> StageCheckpoint): ProvisioningOperation = operation.copy(
        state = state, checkpoints = operation.checkpoints.map { if (it.stage == stage) transform(it) else it },
    )
}
