package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import java.time.Clock
import org.slf4j.LoggerFactory
import org.springframework.web.client.HttpStatusCodeException

enum class StageObservation { SATISFIED, NEEDS_APPLY, WAITING }

class ProvisioningStageContext(
    val operation: ProvisioningOperation,
    private val leaseAssertion: () -> Unit,
    private val resourceCapture: (String, String) -> Unit,
    private val resourceSnapshot: (String) -> String?,
) {
    fun assertLease() = leaseAssertion()
    fun captureResource(key: String, snapshot: String) = resourceCapture(key, snapshot)
    fun resourceSnapshot(key: String): String? = resourceSnapshot.invoke(key)
}

/** Implementations reconcile remote state and retain encrypted ownership snapshots before writes. */
interface ProvisioningStageHandler {
    val stage: ProvisioningStage
    fun reconcile(context: ProvisioningStageContext): StageObservation
    fun apply(context: ProvisioningStageContext): StageObservation
    fun compensate(context: ProvisioningStageContext): StageObservation
}

class ProvisioningStepException(val failure: ProvisioningFailure) : RuntimeException(failure.code)

class ProvisioningExecutor(
    private val journal: ProvisioningJournal,
    handlers: List<ProvisioningStageHandler>,
    private val resources: ProvisioningResourceStore? = null,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val handlers = handlers.associateBy { it.stage }
    private val transitions = ProvisioningTransitions()
    init { require(this.handlers.size == ProvisioningStage.values().size && handlers.size == this.handlers.size) }

    fun advance(environment: String, operationId: String) {
        val lease = journal.claim(environment, operationId, clock.instant(), 60_000) ?: return
        if (lease.operation.state in setOf(ProvisioningState.CANCEL_REQUESTED, ProvisioningState.CANCELLING)) {
            compensate(lease)
        } else {
            execute(lease)
        }
    }

    private fun execute(lease: ProvisioningLease) {
        val stage = transitions.next(lease.operation) ?: return
        val handler = handlers.getValue(stage)
        val running = journal.checkpoint(lease, transitions.started(lease.operation, stage), clock.instant(), false)
        if (running.state == ProvisioningState.CANCEL_REQUESTED) {
            journal.checkpoint(lease, running, clock.instant(), true)
            return
        }
        logger.info("provision stage start operation={} serial={} stage={}", running.id, running.serial, stage)
        try {
            val context = context(lease, running, rejectCancellation = true)
            val observed = handler.reconcile(context)
            val result = if (observed == StageObservation.NEEDS_APPLY) handler.apply(context) else observed
            val next = if (result == StageObservation.SATISFIED) transitions.finished(running, stage)
                else transitions.waiting(running, stage)
            if (next.state == ProvisioningState.WAITING) journal.defer(lease, clock.instant().plusSeconds(5))
            journal.checkpoint(lease, next, clock.instant(), true)
            logger.info("provision stage done operation={} serial={} stage={} result={}", running.id, running.serial, stage, result)
        } catch (ex: Exception) {
            recordFailure(lease, running, stage, ex)
        }
    }

    private fun compensate(lease: ProvisioningLease) {
        val operation = lease.operation
        val stage = transitions.nextCompensation(operation)
        if (stage == null) {
            journal.checkpoint(lease, operation.copy(state = ProvisioningState.CANCELLED), clock.instant(), true)
            return
        }
        logger.info("provision compensate start operation={} serial={} stage={}", operation.id, operation.serial, stage)
        try {
            val result = handlers.getValue(stage).compensate(context(lease, operation, rejectCancellation = false))
            val next = if (result == StageObservation.SATISFIED) transitions.compensated(operation, stage)
                else operation.copy(state = ProvisioningState.CANCELLING)
            if (result != StageObservation.SATISFIED) journal.defer(lease, clock.instant().plusSeconds(5))
            journal.checkpoint(lease, next, clock.instant(), true)
            logger.info("provision compensate done operation={} serial={} stage={} result={}", operation.id, operation.serial, stage, result)
        } catch (ex: Exception) {
            recordFailure(lease, operation, stage, ex)
        }
    }

    private fun assertLease(lease: ProvisioningLease) {
        check(journal.owns(lease, clock.instant())) { "LEASE_LOST" }
    }

    private fun context(lease: ProvisioningLease, operation: ProvisioningOperation, rejectCancellation: Boolean) =
        ProvisioningStageContext(operation, {
            assertLease(lease)
            if (rejectCancellation) {
                check(journal.get(operation.environment, operation.id)?.state != ProvisioningState.CANCEL_REQUESTED) {
                    "CANCELLATION_REQUESTED"
                }
            }
        }, { key, snapshot ->
            requireNotNull(resources) { "RESOURCE_STORE_REQUIRED" }.capture(lease, key, snapshot, clock.instant())
        }, { key -> resources?.snapshot(operation.environment, operation.id, key) })

    private fun recordFailure(lease: ProvisioningLease, operation: ProvisioningOperation, stage: ProvisioningStage, ex: Exception) {
        assertLease(lease)
        val failure = (ex as? ProvisioningStepException)?.failure
            ?: explainedFailure(ex)
            ?: ProvisioningFailure("STAGE_EXECUTION_FAILED", publicDetail(ex), true)
        logger.warn(
            "provision stage failed operation={} serial={} stage={} code={} detail={}",
            operation.id, operation.serial, stage, failure.code, detail(ex),
        )
        journal.checkpoint(lease, transitions.failed(operation, stage, failure), clock.instant(), true)
    }

    private fun publicDetail(ex: Throwable): String {
        val http = generateSequence(ex) { it.cause }.filterIsInstance<HttpStatusCodeException>().firstOrNull()
        val fromBody = http?.responseBodyAsString?.let { remoteReason(it) }
        val fromCause = generateSequence(ex) { it.cause }
            .mapNotNull { it.message?.takeIf { message -> message.isNotBlank() && !isStatusEnvelope(message) } }
            .firstOrNull()
        val text = fromBody ?: fromCause ?: "No se pudo confirmar el paso."
        return text.replace(SECRET, "$1=<redacted>").replace(Regex("\\s+"), " ").take(300)
    }

    private fun remoteReason(body: String): String? {
        val message = jsonField(body, "message")
        if (!message.isNullOrBlank() && !genericStatus(message)) return message
        val error = jsonField(body, "error")
        if (!error.isNullOrBlank() && !genericStatus(error)) return error
        return null
    }

    private fun jsonField(body: String, name: String) =
        Regex("\"$name\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(body)?.groupValues?.get(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")

    private fun genericStatus(value: String) =
        value.equals("Internal Server Error", ignoreCase = true) ||
            value.equals("Error interno del servidor", ignoreCase = true)

    private fun isStatusEnvelope(message: String) =
        message.contains("\"timestamp\"") && message.contains("\"status\"")

    private fun detail(ex: Throwable): String = generateSequence(ex) { it.cause }.take(6).joinToString(" | ") { throwable ->
        val http = throwable as? HttpStatusCodeException
        if (http != null) {
            val body = http.responseBodyAsString.replace(SECRET, "$1=<redacted>").replace(Regex("\\s+"), " ").take(400)
            "${throwable.javaClass.simpleName} ${http.statusCode.value()} $body"
        } else {
            "${throwable.javaClass.simpleName}: ${(throwable.message ?: "").replace(SECRET, "$1=<redacted>").take(300)}"
        }
    }

    private fun explainedFailure(ex: Exception): ProvisioningFailure? {
        val http = generateSequence<Throwable>(ex) { it.cause }.filterIsInstance<HttpStatusCodeException>().firstOrNull()
        val raw = http?.responseBodyAsString?.ifBlank { http.statusText }.orEmpty()
        if (raw.contains("already reserved", ignoreCase = true) ||
            raw.contains("already exists outside", ignoreCase = true)
        ) {
            return ProvisioningFailure(
                "ONU_ALREADY_RESERVED",
                "La ONU ya está registrada por otra alta. Libérala en la OLT antes de reintentar.",
                false,
            )
        }
        val detail = publicDetail(ex)
        val code = Regex("^[A-Z][A-Z0-9_]{2,}").find(detail)?.value ?: return null
        return ProvisioningFailure(code, detail, code !in NON_RETRYABLE)
    }

    private companion object {
        val logger = LoggerFactory.getLogger(ProvisioningExecutor::class.java)
        val SECRET = Regex("(?i)(password|passwd|passphrase|secret|authorization)(\"?\\s*[:=]\\s*\"?)[^\"\\s,}]+")
        val NON_RETRYABLE = setOf(
            "ONU_ALREADY_RESERVED",
            "OMCI_EXISTING_SERVER_CONFLICT",
            "OMCI_EXISTING_WAN_CONFLICT",
            "OMCI_IDENTITY_MISMATCH",
        )
    }
}
