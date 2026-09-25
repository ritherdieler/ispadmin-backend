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
        jdbc.update("INSERT INTO provisioning_v2_operation (operation_id, environment, subscription_id, serial, revision, operation_json, state) VALUES (?,?,?,?,?,?,?)",
            operation.id, operation.environment, operation.subscriptionId, operation.serial, operation.revision,
            json.writeValueAsString(operation), operation.state.name)
        appendEvent(operation)
        operation
    }

    fun get(environment: String, id: String): ProvisioningOperation? = read(environment, id, false)

    fun latest(environment: String, subscriptionId: Int): ProvisioningOperation? = jdbc.query(
        """SELECT o.operation_json FROM provisioning_v2_operation o
            JOIN provisioning_v2_event e ON e.operation_id=o.operation_id
            WHERE o.environment=? AND o.subscription_id=?
            GROUP BY o.operation_id, o.operation_json ORDER BY MIN(e.id) DESC LIMIT 1""",
        { rs, _ -> json.readValue(rs.getString("operation_json"), ProvisioningOperation::class.java) },
        environment, subscriptionId).singleOrNull()

    fun history(environment: String, id: String, after: Long): List<ProvisioningEvent> {
        require(after >= 0)
        return jdbc.query("""SELECT e.id, e.payload_json FROM provisioning_v2_event e
            JOIN provisioning_v2_operation o ON o.operation_id=e.operation_id
            WHERE o.environment=? AND o.operation_id=? AND e.id>? ORDER BY e.id LIMIT 100""",
            { rs, _ -> ProvisioningEvent(rs.getLong("id"), json.readValue(rs.getString("payload_json"), ProvisioningOperation::class.java)) },
            environment, id, after)
    }

    fun claim(environment: String, id: String, now: Instant, durationMs: Long): ProvisioningLease? = transaction {
        require(durationMs in 1..300_000) { "INVALID_LEASE_DURATION" }
        val token = UUID.randomUUID().toString()
        val changed = jdbc.update("""UPDATE provisioning_v2_operation SET lease_token=?, lease_until=?
            WHERE environment=? AND operation_id=? AND lease_until<=? AND next_attempt_at<=?
            AND state IN ('PENDING','RUNNING','WAITING','CANCEL_REQUESTED','CANCELLING')""",
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
        { rs, _ -> ProvisioningEvent(rs.getLong("id"), json.readValue(rs.getString("payload_json"), ProvisioningOperation::class.java)) })

    fun delivered(eventId: Long) { jdbc.update("UPDATE provisioning_v2_event SET delivered=TRUE WHERE id=?", eventId) }

    fun owns(lease: ProvisioningLease, now: Instant): Boolean = jdbc.queryForObject(
        "SELECT COUNT(*) FROM provisioning_v2_operation WHERE environment=? AND operation_id=? AND lease_token=? AND lease_until>?",
        Long::class.java, lease.operation.environment, lease.operation.id, lease.token, now.toEpochMilli()) == 1L

    fun due(environment: String, now: Instant): List<String> = jdbc.query(
        """SELECT operation_id FROM provisioning_v2_operation WHERE environment=? AND lease_until<=? AND next_attempt_at<=?
            AND state IN ('PENDING','RUNNING','WAITING','CANCEL_REQUESTED','CANCELLING') ORDER BY next_attempt_at LIMIT 50""",
        { rs, _ -> rs.getString("operation_id") }, environment, now.toEpochMilli(), now.toEpochMilli())

    fun defer(lease: ProvisioningLease, until: Instant) {
        check(jdbc.update("UPDATE provisioning_v2_operation SET next_attempt_at=? WHERE environment=? AND operation_id=? AND lease_token=?",
            until.toEpochMilli(), lease.operation.environment, lease.operation.id, lease.token) == 1) { "LEASE_LOST" }
    }

    private fun appendEvent(operation: ProvisioningOperation) {
        jdbc.update("INSERT INTO provisioning_v2_event (operation_id, payload_json) VALUES (?,?)", operation.id, json.writeValueAsString(operation))
    }

    private fun read(environment: String, id: String, locked: Boolean): ProvisioningOperation? = jdbc.query(
        "SELECT operation_json FROM provisioning_v2_operation WHERE environment=? AND operation_id=?" + if (locked) " FOR UPDATE" else "",
        { rs, _ -> json.readValue(rs.getString("operation_json"), ProvisioningOperation::class.java) }, environment, id).singleOrNull()

    private fun <T> transaction(block: () -> T): T {
        val result = transactions.execute { Box(block()) }
        return requireNotNull(result).value
    }
    private data class Box<T>(val value: T)
}
