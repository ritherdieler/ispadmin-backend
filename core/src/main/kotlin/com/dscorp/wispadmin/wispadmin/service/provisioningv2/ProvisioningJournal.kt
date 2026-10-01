package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.transaction.PlatformTransactionManager

data class ProvisioningLease(val operation: ProvisioningOperation, val token: String)
data class ProvisioningEvent(val id: Long, val operation: ProvisioningOperation)

/** No secrets belong in operation_json or the outbox. Resource snapshots need a separate encrypted store. */
class ProvisioningJournal(
    private val jdbc: JdbcTemplate,
    private val json: ObjectMapper,
    transactionManager: PlatformTransactionManager = DataSourceTransactionManager(requireNotNull(jdbc.dataSource)),
) {
    private val transactions = TransactionTemplate(transactionManager)
    private val transitions = ProvisioningTransitions()

    fun insert(operation: ProvisioningOperation): ProvisioningOperation = transaction {
        require(operation.state == ProvisioningState.PENDING && operation.checkpoints.none { it.touched })
        insertRow(operation)
        appendEvent(operation)
        operation
    }

    fun insertPreauthorization(operation: ProvisioningOperation): ProvisioningOperation = transaction {
        require(operation.flowVersion == 3 && operation.phase == ProvisioningPhase.OLT_AUTHORIZATION)
        require(operation.subscriptionId == null && operation.operatorId != null && operation.onuTarget != null)
        require(operation.state == ProvisioningState.PENDING && operation.checkpoints.none { it.touched })
        insertRow(operation)
        jdbc.update("INSERT INTO provisioning_v2_operator_lock (operator_id, environment, operation_id, created_at_epoch_ms) VALUES (?,?,?,?)",
            operation.operatorId, operation.environment, operation.id, operation.updatedAt.toEpochMilli())
        appendEvent(operation)
        operation
    }

    fun activeForOperator(environment: String, operatorId: Long): ProvisioningOperation? = jdbc.query(
        """SELECT o.operation_json FROM provisioning_v2_operation o
            JOIN provisioning_v2_operator_lock l ON l.operation_id=o.operation_id AND l.environment=o.environment
            WHERE o.environment=? AND l.operator_id=?""",
        { rs, _ -> readOperation(rs.getString("operation_json")) },
        environment, operatorId).singleOrNull()

    fun preauthorizationForRequest(environment: String, operatorId: Long, requestKey: String): ProvisioningOperation? = jdbc.query(
        """SELECT operation_json FROM provisioning_v2_operation
            WHERE environment=? AND operator_id=? AND registration_request_key=?""",
        { rs, _ -> readOperation(rs.getString("operation_json")) },
        environment, operatorId, requestKey).singleOrNull()

    fun unlinkedPreauthorizations(environment: String): List<ProvisioningOperation> = jdbc.query(
        """SELECT operation_json FROM provisioning_v2_operation
            WHERE environment=? AND subscription_id IS NULL AND phase<>'PROVISIONING' AND state<>'CANCELLED'
            ORDER BY operation_id DESC LIMIT 200""",
        { rs, _ -> readOperation(rs.getString("operation_json")) },
        environment)

    fun updatePreauthorization(
        environment: String,
        id: String,
        expectedRevision: Long,
        transform: (ProvisioningOperation) -> ProvisioningOperation,
        now: Instant = Instant.now(),
    ): ProvisioningOperation = transaction {
        val current = read(environment, id, true) ?: throw NoSuchElementException("OPERATION_NOT_FOUND")
        require(current.revision == expectedRevision) { "STALE_REVISION" }
        require(current.subscriptionId == null && current.phase != ProvisioningPhase.PROVISIONING) { "NOT_PREAUTHORIZATION" }
        val changed = transform(current).copy(revision = current.revision + 1, updatedAt = now)
        require(changed.id == current.id && changed.environment == current.environment &&
            changed.serial == current.serial && changed.operatorId == current.operatorId) { "OPERATION_IDENTITY_CHANGED" }
        persistUnleased(changed, current.revision)
        appendEvent(changed)
        releaseOperatorLockIfTerminal(changed)
        changed
    }

    fun promotePreauthorization(
        environment: String,
        id: String,
        operatorId: Long,
        subscriptionId: Int,
        now: Instant = Instant.now(),
    ): ProvisioningOperation = transaction {
        require(subscriptionId > 0)
        val current = read(environment, id, true) ?: throw NoSuchElementException("OPERATION_NOT_FOUND")
        require(current.operatorId == operatorId && current.subscriptionId == null) { "PREAUTHORIZATION_OWNER_MISMATCH" }
        require(current.phase == ProvisioningPhase.READY_FOR_FORM && current.state == ProvisioningState.READY_FOR_FORM) {
            "ACS_CONFIRMATION_REQUIRED"
        }
        val checkpoints = current.checkpoints.map { checkpoint ->
            if (checkpoint.stage in PRECONFIGURED_STAGES) checkpoint.copy(
                state = CheckpointState.SUCCEEDED,
                touched = checkpoint.stage == ProvisioningStage.OLT,
                failure = null,
            ) else checkpoint
        }
        val promoted = current.copy(
            subscriptionId = subscriptionId,
            phase = ProvisioningPhase.PROVISIONING,
            state = ProvisioningState.PENDING,
            checkpoints = checkpoints,
            revision = current.revision + 1,
            updatedAt = now,
        )
        persistUnleased(promoted, current.revision)
        appendEvent(promoted)
        promoted
    }

    fun get(environment: String, id: String): ProvisioningOperation? = read(environment, id, false)

    fun latest(environment: String, subscriptionId: Int): ProvisioningOperation? = jdbc.query(
        """SELECT o.operation_json FROM provisioning_v2_operation o
            JOIN provisioning_v2_event e ON e.operation_id=o.operation_id
            WHERE o.environment=? AND o.subscription_id=?
            GROUP BY o.operation_id, o.operation_json ORDER BY MIN(e.id) DESC LIMIT 1""",
        { rs, _ -> readOperation(rs.getString("operation_json")) },
        environment, subscriptionId).singleOrNull()

    fun history(environment: String, id: String, after: Long): List<ProvisioningEvent> {
        require(after >= 0)
        return jdbc.query("""SELECT e.id, e.payload_json FROM provisioning_v2_event e
            JOIN provisioning_v2_operation o ON o.operation_id=e.operation_id
            WHERE o.environment=? AND o.operation_id=? AND e.id>? ORDER BY e.id LIMIT 100""",
            { rs, _ -> ProvisioningEvent(rs.getLong("id"), readOperation(rs.getString("payload_json"))) },
            environment, id, after)
    }

    fun claim(environment: String, id: String, now: Instant, durationMs: Long): ProvisioningLease? = transaction {
        require(durationMs in 1..300_000) { "INVALID_LEASE_DURATION" }
        val token = UUID.randomUUID().toString()
        val changed = jdbc.update("""UPDATE provisioning_v2_operation SET lease_token=?, lease_until=?
            WHERE environment=? AND operation_id=? AND lease_until<=? AND next_attempt_at<=?
            AND ((phase='PROVISIONING' AND state IN ('PENDING','RUNNING','WAITING','CANCEL_REQUESTED','CANCELLING'))
                OR state IN ('CANCEL_REQUESTED','CANCELLING'))""",
            token, now.toEpochMilli() + durationMs, environment, id, now.toEpochMilli(), now.toEpochMilli())
        if (changed == 0) null else ProvisioningLease(requireNotNull(get(environment, id)), token)
    }

    fun checkpoint(lease: ProvisioningLease, operation: ProvisioningOperation, now: Instant, release: Boolean): ProvisioningOperation = transaction {
        require(operation.id == lease.operation.id && operation.environment == lease.operation.environment &&
            operation.revision == lease.operation.revision && operation.subscriptionId == lease.operation.subscriptionId &&
            operation.serial == lease.operation.serial) { "OPERATION_IDENTITY_CHANGED" }
        val current = requireNotNull(read(operation.environment, operation.id, true))
        val next = operation.copy(updatedAt = now, state = if (current.state == ProvisioningState.CANCEL_REQUESTED &&
            operation.state !in setOf(ProvisioningState.CANCELLED, ProvisioningState.CANCELLING, ProvisioningState.CANCEL_FAILED))
                ProvisioningState.CANCEL_REQUESTED else operation.state)
        val changed = jdbc.update("""UPDATE provisioning_v2_operation SET operation_json=?, state=?,
            lease_token=CASE WHEN ? THEN NULL ELSE lease_token END,
            lease_until=CASE WHEN ? THEN 0 ELSE lease_until END
            WHERE environment=? AND operation_id=? AND revision=? AND lease_token=? AND lease_until>?""",
            json.writeValueAsString(next), next.state.name, release, release, next.environment, next.id, next.revision,
            lease.token, now.toEpochMilli())
        check(changed == 1) { "LEASE_LOST" }
        appendEvent(next)
        releaseOperatorLockIfTerminal(next)
        next
    }

    fun requestCancel(environment: String, id: String, revision: Long): ProvisioningOperation = transaction {
        val current = read(environment, id, true) ?: throw NoSuchElementException("OPERATION_NOT_FOUND")
        val next = transitions.cancel(current, revision)
        if (next != current) {
            jdbc.update("UPDATE provisioning_v2_operation SET operation_json=?, state=?, cancel_requested=TRUE, next_attempt_at=0 WHERE environment=? AND operation_id=?",
                json.writeValueAsString(next), next.state.name, environment, id)
            appendEvent(next)
        }
        next
    }

    fun requestRegistrationCancel(environment: String, id: String, operatorId: Long, revision: Long): ProvisioningOperation = transaction {
        val current = read(environment, id, true) ?: throw NoSuchElementException("OPERATION_NOT_FOUND")
        require(current.operatorId == operatorId && current.flowVersion == 3) { "REGISTRATION_OPERATOR_MISMATCH" }
        val next = transitions.cancelRegistration(current, revision)
        if (next != current) {
            if (current.state == ProvisioningState.SUCCEEDED) {
                jdbc.update(
                    "INSERT INTO provisioning_v2_operator_lock (operator_id, environment, operation_id, created_at_epoch_ms) VALUES (?,?,?,?)",
                    operatorId, environment, id, current.updatedAt.toEpochMilli(),
                )
            }
            jdbc.update("UPDATE provisioning_v2_operation SET operation_json=?, state=?, cancel_requested=TRUE, next_attempt_at=0 WHERE environment=? AND operation_id=?",
                json.writeValueAsString(next), next.state.name, environment, id)
            appendEvent(next)
        }
        next
    }

    fun requestRetry(environment: String, id: String, revision: Long): ProvisioningOperation = transaction {
        val current = read(environment, id, true) ?: throw NoSuchElementException("OPERATION_NOT_FOUND")
        val next = transitions.retry(current, revision)
        if (next != current) {
            jdbc.update("UPDATE provisioning_v2_operation SET operation_json=?, state=?, next_attempt_at=0 WHERE environment=? AND operation_id=?",
                json.writeValueAsString(next), next.state.name, environment, id)
            appendEvent(next)
        }
        next
    }

    fun events(): List<ProvisioningEvent> = jdbc.query(
        "SELECT id, payload_json FROM provisioning_v2_event WHERE delivered=FALSE ORDER BY id LIMIT 100",
        { rs, _ -> ProvisioningEvent(rs.getLong("id"), readOperation(rs.getString("payload_json"))) })

    fun delivered(eventId: Long) { jdbc.update("UPDATE provisioning_v2_event SET delivered=TRUE WHERE id=?", eventId) }

    fun owns(lease: ProvisioningLease, now: Instant): Boolean = jdbc.queryForObject(
        "SELECT COUNT(*) FROM provisioning_v2_operation WHERE environment=? AND operation_id=? AND lease_token=? AND lease_until>?",
        Long::class.java, lease.operation.environment, lease.operation.id, lease.token, now.toEpochMilli()) == 1L

    fun due(environment: String, now: Instant): List<String> = jdbc.query(
        """SELECT operation_id FROM provisioning_v2_operation WHERE environment=? AND lease_until<=? AND next_attempt_at<=?
            AND ((phase='PROVISIONING' AND state IN ('PENDING','RUNNING','WAITING','CANCEL_REQUESTED','CANCELLING'))
                OR state IN ('CANCEL_REQUESTED','CANCELLING')) ORDER BY next_attempt_at LIMIT 50""",
        { rs, _ -> rs.getString("operation_id") }, environment, now.toEpochMilli(), now.toEpochMilli())

    fun defer(lease: ProvisioningLease, until: Instant) {
        check(jdbc.update("UPDATE provisioning_v2_operation SET next_attempt_at=? WHERE environment=? AND operation_id=? AND lease_token=?",
            until.toEpochMilli(), lease.operation.environment, lease.operation.id, lease.token) == 1) { "LEASE_LOST" }
    }

    private fun appendEvent(operation: ProvisioningOperation) {
        jdbc.update("INSERT INTO provisioning_v2_event (operation_id, payload_json) VALUES (?,?)", operation.id, json.writeValueAsString(operation))
    }

    private fun insertRow(operation: ProvisioningOperation) {
        jdbc.update("""INSERT INTO provisioning_v2_operation
            (operation_id, environment, subscription_id, serial, revision, operation_json, state, phase,
             operator_id, operator_username, registration_request_key)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)""",
            operation.id, operation.environment, operation.subscriptionId, operation.serial, operation.revision,
            json.writeValueAsString(operation), operation.state.name, operation.phase.name,
            operation.operatorId, operation.operatorUsername, operation.registrationRequestKey)
    }

    private fun persistUnleased(operation: ProvisioningOperation, expectedRevision: Long) {
        val changed = jdbc.update("""UPDATE provisioning_v2_operation SET subscription_id=?, revision=?, operation_json=?, state=?, phase=?,
            operator_id=?, operator_username=?, registration_request_key=?, cancel_requested=?, next_attempt_at=0
            WHERE environment=? AND operation_id=? AND revision=? AND lease_token IS NULL""",
            operation.subscriptionId, operation.revision, json.writeValueAsString(operation), operation.state.name, operation.phase.name,
            operation.operatorId, operation.operatorUsername, operation.registrationRequestKey,
            operation.state in setOf(ProvisioningState.CANCEL_REQUESTED, ProvisioningState.CANCELLING),
            operation.environment, operation.id, expectedRevision)
        check(changed == 1) { "LEASE_LOST_OR_STALE_REVISION" }
    }

    private fun releaseOperatorLockIfTerminal(operation: ProvisioningOperation) {
        val cancellationCleanupPending = operation.state == ProvisioningState.CANCELLED && operation.subscriptionId != null
        if (operation.state == ProvisioningState.SUCCEEDED ||
            operation.state == ProvisioningState.CANCELLED && !cancellationCleanupPending) {
            operation.operatorId?.let { operatorId ->
                jdbc.update("DELETE FROM provisioning_v2_operator_lock WHERE operator_id=? AND operation_id=? AND environment=?",
                    operatorId, operation.id, operation.environment)
            }
        }
    }

    private fun read(environment: String, id: String, locked: Boolean): ProvisioningOperation? = jdbc.query(
        "SELECT operation_json FROM provisioning_v2_operation WHERE environment=? AND operation_id=?" + if (locked) " FOR UPDATE" else "",
        { rs, _ -> readOperation(rs.getString("operation_json")) }, environment, id).singleOrNull()

    /** Drops retired checkpoints and adds the durable cleanup checkpoint to older operations. */
    private fun readOperation(payload: String): ProvisioningOperation {
        val upgraded = json.readTree(payload).deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
        upgraded.remove("managementMode")
        val source = upgraded.path("checkpoints")
        if (!source.isArray) return json.treeToValue(upgraded, ProvisioningOperation::class.java)
        val checkpoints = upgraded.withArray("checkpoints")
        val supportedStages = ProvisioningStage.values().map { it.name }.toSet()
        for (index in checkpoints.size() - 1 downTo 0) {
            if (checkpoints[index].path("stage").asText() !in supportedStages) checkpoints.remove(index)
        }
        if (checkpoints.any { it.path("stage").asText() == ProvisioningStage.WAN_CLEANUP.name }) {
            return json.treeToValue(upgraded, ProvisioningOperation::class.java)
        }
        val verifyIndex = checkpoints.indexOfFirst { it.path("stage").asText() == ProvisioningStage.VERIFY.name }
            .takeIf { it >= 0 } ?: checkpoints.size()
        val verifyState = if (verifyIndex < checkpoints.size()) checkpoints.get(verifyIndex).path("state").asText() else null
        val state = when {
            upgraded.path("state").asText() == ProvisioningState.SUCCEEDED.name || verifyState == CheckpointState.SUCCEEDED.name -> CheckpointState.SUCCEEDED.name
            upgraded.path("state").asText() == ProvisioningState.CANCELLED.name -> CheckpointState.COMPENSATED.name
            else -> CheckpointState.PENDING.name
        }
        val cleanup = json.createObjectNode().apply {
            put("stage", ProvisioningStage.WAN_CLEANUP.name)
            put("state", state)
            put("attempts", 0)
            put("touched", false)
            set<com.fasterxml.jackson.databind.JsonNode>("failure", json.nullNode())
        }
        checkpoints.insert(verifyIndex, cleanup)
        return json.treeToValue(upgraded, ProvisioningOperation::class.java)
    }

    private fun <T> transaction(block: () -> T): T {
        val result = transactions.execute { Box(block()) }
        return requireNotNull(result).value
    }
    private data class Box<T>(val value: T)

    private companion object {
        val PRECONFIGURED_STAGES = setOf(
            ProvisioningStage.OLT,
            ProvisioningStage.ACS_CONTACT,
        )
    }
}
