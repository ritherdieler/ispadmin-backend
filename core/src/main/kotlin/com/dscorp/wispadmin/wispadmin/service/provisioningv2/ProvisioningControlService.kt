package com.dscorp.wispadmin.wispadmin.service.provisioningv2

data class ProvisioningActionRequest(val operationId: String, val expectedRevision: Long)
data class ProvisioningProgress(
    val operation: ProvisioningOperation,
    val canRetry: Boolean,
    val canCancel: Boolean,
    val canRetryCancellation: Boolean,
    val canStartAgain: Boolean,
)

/** Environment is server-supplied; never take it from a public request body. */
class ProvisioningControlService(private val journal: ProvisioningJournal, private val environment: String) {
    init { require(environment.isNotBlank()) }
    fun progress(subscriptionId: Int, operationId: String): ProvisioningProgress = view(owned(subscriptionId, operationId))

    fun latest(subscriptionId: Int): ProvisioningProgress = view(
        journal.latest(environment, subscriptionId) ?: throw NoSuchElementException("OPERATION_NOT_FOUND"))

    fun history(subscriptionId: Int, operationId: String, after: Long): List<ProvisioningEvent> {
        owned(subscriptionId, operationId)
        return journal.history(environment, operationId, after)
    }

    fun retry(subscriptionId: Int, request: ProvisioningActionRequest): ProvisioningProgress {
        owned(subscriptionId, request.operationId)
        return view(journal.requestRetry(environment, request.operationId, request.expectedRevision))
    }

    fun cancel(subscriptionId: Int, request: ProvisioningActionRequest): ProvisioningProgress {
        owned(subscriptionId, request.operationId)
        return view(journal.requestCancel(environment, request.operationId, request.expectedRevision))
    }

    private fun owned(subscriptionId: Int, id: String): ProvisioningOperation = journal.get(environment, id)
        ?.takeIf { it.subscriptionId == subscriptionId && it.environment == environment }
        ?: throw NoSuchElementException("OPERATION_NOT_FOUND")

    private fun view(operation: ProvisioningOperation) = ProvisioningProgress(
        operation = operation,
        canRetry = operation.state == ProvisioningState.FAILED && operation.checkpoints.none { it.failure?.retryable == false },
        canCancel = operation.state in setOf(ProvisioningState.PENDING, ProvisioningState.RUNNING, ProvisioningState.WAITING, ProvisioningState.FAILED),
        canRetryCancellation = operation.state == ProvisioningState.CANCEL_FAILED,
        canStartAgain = operation.state == ProvisioningState.CANCELLED,
    )
}
