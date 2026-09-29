package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmSecretCipher
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.transaction.PlatformTransactionManager

/** Immutable encrypted snapshots; these payloads must never be returned in progress or telemetry. */
class ProvisioningResourceStore(
    private val jdbc: JdbcTemplate,
    private val cipher: CrmSecretCipher,
    transactionManager: PlatformTransactionManager = DataSourceTransactionManager(requireNotNull(jdbc.dataSource)),
) {
    private val transactions = TransactionTemplate(transactionManager)

    /** Called in the registration transaction, before a worker can claim the new operation. */
    fun captureInitial(operation: ProvisioningOperation, resourceKey: String, snapshot: String) {
        require(resourceKey.matches(Regex("[a-zA-Z0-9._:-]{1,128}")))
        require(snapshot.length in 1..262144)
        transactions.executeWithoutResult {
            val states = jdbc.query("""SELECT state FROM provisioning_v2_operation
                WHERE environment=? AND operation_id=? AND subscription_id=? AND serial=? FOR UPDATE""",
                { rs, _ -> rs.getString("state") }, operation.environment, operation.id, operation.subscriptionId, operation.serial)
            check(states.singleOrNull() == ProvisioningState.PENDING.name) { "INITIAL_SNAPSHOT_NOT_ALLOWED" }
            val existing = snapshot(operation.environment, operation.id, resourceKey)
            if (existing != null) {
                check(existing == snapshot) { "RESOURCE_BASELINE_CONFLICT" }
            } else {
                jdbc.update("INSERT INTO provisioning_v2_resource (operation_id, resource_key, snapshot_cipher) VALUES (?,?,?)",
                    operation.id, resourceKey, cipher.encrypt(snapshot))
            }
        }
    }

    fun savePreauthorizationResource(
        environment: String,
        operationId: String,
        operatorId: Long,
        resourceKey: String,
        value: String,
    ) {
        require(resourceKey in PREAUTHORIZATION_RESOURCE_KEYS)
        require(value.length in 1..262144)
        transactions.executeWithoutResult {
            val operation = jdbc.query("""SELECT operator_id, subscription_id, phase, state FROM provisioning_v2_operation
                WHERE environment=? AND operation_id=? FOR UPDATE""",
                { rs, _ -> listOf(rs.getLong("operator_id"), rs.getObject("subscription_id"), rs.getString("phase"), rs.getString("state")) },
                environment, operationId).singleOrNull() ?: throw NoSuchElementException("OPERATION_NOT_FOUND")
            require(operation[0] == operatorId && operation[1] == null &&
                operation[2] == ProvisioningPhase.READY_FOR_FORM.name && operation[3] == ProvisioningState.READY_FOR_FORM.name) {
                "PREAUTHORIZATION_DRAFT_NOT_ALLOWED"
            }
            jdbc.update("""INSERT INTO provisioning_v2_resource (operation_id, resource_key, snapshot_cipher) VALUES (?,?,?)
                ON DUPLICATE KEY UPDATE snapshot_cipher=VALUES(snapshot_cipher)""",
                operationId, resourceKey, cipher.encrypt(value))
        }
    }

    fun preauthorizationResource(environment: String, operationId: String, operatorId: Long, resourceKey: String): String? {
        require(resourceKey in PREAUTHORIZATION_RESOURCE_KEYS)
        val owner = jdbc.query("""SELECT operator_id, subscription_id, phase FROM provisioning_v2_operation
            WHERE environment=? AND operation_id=?""",
            { rs, _ -> Triple(rs.getLong("operator_id"), rs.getObject("subscription_id"), rs.getString("phase")) },
            environment, operationId).singleOrNull() ?: throw NoSuchElementException("OPERATION_NOT_FOUND")
        require(owner.first == operatorId && owner.second == null && owner.third == ProvisioningPhase.READY_FOR_FORM.name) {
            "PREAUTHORIZATION_DRAFT_NOT_ALLOWED"
        }
        return snapshot(environment, operationId, resourceKey)
    }

    fun capture(lease: ProvisioningLease, resourceKey: String, snapshot: String, now: Instant) {
        require(resourceKey.matches(Regex("[a-zA-Z0-9._:-]{1,128}")))
        require(snapshot.length in 1..262144)
        transactions.executeWithoutResult {
            val tokens = jdbc.query("""SELECT lease_token FROM provisioning_v2_operation
                WHERE environment=? AND operation_id=? AND revision=? AND lease_until>? FOR UPDATE""",
                { rs, _ -> rs.getString("lease_token") }, lease.operation.environment, lease.operation.id,
                lease.operation.revision, now.toEpochMilli())
            check(tokens.singleOrNull() == lease.token) { "LEASE_LOST" }
            val existing = snapshot(lease.operation.environment, lease.operation.id, resourceKey)
            if (existing != null) {
                check(existing == snapshot) { "RESOURCE_BASELINE_CONFLICT" }
            } else {
                jdbc.update("INSERT INTO provisioning_v2_resource (operation_id, resource_key, snapshot_cipher) VALUES (?,?,?)",
                    lease.operation.id, resourceKey, cipher.encrypt(snapshot))
            }
        }
    }

    fun snapshot(environment: String, operationId: String, resourceKey: String): String? = jdbc.query(
        """SELECT r.snapshot_cipher FROM provisioning_v2_resource r JOIN provisioning_v2_operation o ON o.operation_id=r.operation_id
            WHERE o.environment=? AND o.operation_id=? AND r.resource_key=?""",
        { rs, _ -> cipher.decrypt(rs.getString("snapshot_cipher")) }, environment, operationId, resourceKey).singleOrNull()

    fun preauthorizationPhotoUrl(environment: String, operationId: String): String? =
        snapshot(environment, operationId, "registration-photo")

    fun deletePreauthorizationResources(environment: String, operationId: String) {
        transactions.executeWithoutResult {
            val state = jdbc.query("""SELECT state FROM provisioning_v2_operation
                WHERE environment=? AND operation_id=? FOR UPDATE""",
                { rs, _ -> rs.getString("state") }, environment, operationId).singleOrNull()
                ?: throw NoSuchElementException("OPERATION_NOT_FOUND")
            require(state in setOf("CANCEL_REQUESTED", "CANCELLING", "CANCEL_FAILED")) { "CANCELLATION_CLEANUP_NOT_ALLOWED" }
            jdbc.update("DELETE FROM provisioning_v2_resource WHERE operation_id=?", operationId)
        }
    }

    private companion object {
        val PREAUTHORIZATION_RESOURCE_KEYS = setOf("registration-draft", "registration-photo")
    }
}
