package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.fasterxml.jackson.databind.ObjectMapper
import com.dscorp.wispadmin.wispadmin.observability.ReportedEvent

class ProvisioningOutbox(
    private val journal: ProvisioningJournal,
    private val json: ObjectMapper,
    private val deliver: (String, String) -> Boolean,
) {
    fun flush() {
        journal.events().forEach { item ->
            try {
                val payload = json.writeValueAsString(mapOf("events" to listOf(event(item))))
                if (deliver("${item.operation.environment}:${item.operation.id}:${item.id}", payload)) journal.delivered(item.id)
            } catch (_: Exception) {
                // Unacknowledged rows remain durable; a failing delivery cannot block another operation.
            }
        }
    }

    private fun event(item: ProvisioningEvent): ReportedEvent {
        val operation = item.operation
        val failed = operation.checkpoints.firstOrNull { it.failure != null }
        val current = failed ?: operation.checkpoints.firstOrNull { it.state != CheckpointState.SUCCEEDED }
        val failure = failed?.failure
        val terminalFailure = operation.state in setOf(ProvisioningState.FAILED, ProvisioningState.CANCEL_FAILED)
        val status = when (operation.state) {
            ProvisioningState.SUCCEEDED -> "success"
            ProvisioningState.FAILED, ProvisioningState.CANCEL_FAILED -> "failed"
            ProvisioningState.CANCELLED -> "cancelled"
            ProvisioningState.WAITING -> "waiting"
            else -> "running"
        }
        return ReportedEvent(
            eventType = if (terminalFailure) "error" else "log", platform = "backend",
            severity = when {
                terminalFailure -> "error"
                failure != null -> "warning"
                else -> "info"
            },
            message = failure?.message ?: "Alta FIBER: ${operation.state}", errorType = failure?.code,
            stacktrace = null, environment = operation.environment, correlationId = operation.id,
            timestamp = operation.updatedAt.takeIf { it != java.time.Instant.EPOCH }?.toEpochMilli(),
            tags = mapOf("feature" to "fiber-onboarding", "workflowId" to operation.id,
                "workflowName" to "Alta FIBER v2", "workflowCategory" to "provisioning",
                "workflowStatus" to status, "subscriptionId" to operation.subscriptionId,
                "serial" to operation.serial, "flowVersion" to operation.flowVersion,
                "revision" to operation.revision, "eventId" to item.id, "phase" to operation.phase.name,
                "stage" to current?.stage?.name, "attempt" to current?.attempts,
                "failureCode" to failure?.code, "retryable" to failure?.retryable),
        )
    }
}
