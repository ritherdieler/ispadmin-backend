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
}
