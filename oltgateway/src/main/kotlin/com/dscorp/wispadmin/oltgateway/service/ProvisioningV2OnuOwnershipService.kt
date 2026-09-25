package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.config.GatewayCallContext
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest

data class ProvisioningV2OnuOwnership(
    val serial: String,
    val operationId: String,
    val callerEnv: String,
    val requestHash: String,
    val externalId: String?,
    val board: Int?,
    val port: Int?,
    val ontId: Int?,
    val stage: String,
)

/**
 * Durable fence for v2 OLT effects. It intentionally contains no PPPoE, Wi-Fi,
 * TR-069, or customer secret; only the identity required to reconcile/delete the ONU.
 */
@Service
class ProvisioningV2OnuOwnershipService(
    private val jdbc: JdbcTemplate,
) {
    fun releaseSerial(serial: String) {
        jdbc.update("DELETE FROM olt_provisioning_v2_onu_operation WHERE sn = ?", serial.trim().uppercase())
    }

    fun find(serial: String): ProvisioningV2OnuOwnership? = findBySerial(serial.trim().uppercase())

    fun listAll(): List<ProvisioningV2OnuOwnership> = jdbc.query(
        """
        SELECT sn, operation_id, caller_env, request_hash, external_id, board, port, ont_id, stage
        FROM olt_provisioning_v2_onu_operation
        """.trimIndent(),
        ownershipRowMapper,
    )

    fun claim(serial: String, operationId: String, requestFingerprint: String): ProvisioningV2OnuOwnership {
        val environment = GatewayCallContext.env()?.trim().orEmpty().ifBlank { "unknown" }
        val existing = findBySerial(serial)
        if (existing != null) {
            val sameOperation = existing.operationId == operationId && existing.callerEnv == environment
            if (sameOperation && existing.requestHash == requestFingerprint) return existing
            if (existing.externalId.isNullOrBlank()) {
                jdbc.update(
                    """
                    UPDATE olt_provisioning_v2_onu_operation
                    SET operation_id=?, caller_env=?, request_hash=?, stage='CLAIMED', updated_at_epoch_ms=?
                    WHERE sn=?
                    """.trimIndent(),
                    operationId,
                    environment,
                    requestFingerprint,
                    System.currentTimeMillis(),
                    serial,
                )
                return findBySerial(serial) ?: error("Provisioning ONU reservation was not updated")
            }
            if (!sameOperation) throw conflict("ONU is already reserved by another provisioning operation")
            throw conflict("Provisioning request differs from the reserved operation")
        }

        val now = System.currentTimeMillis()
        try {
            jdbc.update(
                """
                INSERT INTO olt_provisioning_v2_onu_operation
                    (sn, operation_id, caller_env, request_hash, stage, created_at_epoch_ms, updated_at_epoch_ms)
                VALUES (?, ?, ?, ?, 'CLAIMED', ?, ?)
                """.trimIndent(),
                serial,
                operationId,
                environment,
                requestFingerprint,
                now,
                now,
            )
        } catch (_: DuplicateKeyException) {
            return claim(serial, operationId, requestFingerprint)
        }
        return findBySerial(serial) ?: error("Provisioning v2 ONU reservation was not persisted")
    }

    fun recordAuthorization(
        ownership: ProvisioningV2OnuOwnership,
        externalId: String,
        board: Int,
        port: Int,
        ontId: Int,
    ): ProvisioningV2OnuOwnership {
        requireOwnership(ownership)
        jdbc.update(
            """
            UPDATE olt_provisioning_v2_onu_operation
            SET external_id = ?, board = ?, port = ?, ont_id = ?, stage = 'AUTHORIZED', updated_at_epoch_ms = ?
            WHERE sn = ? AND operation_id = ?
            """.trimIndent(),
            externalId,
            board,
            port,
            ontId,
            System.currentTimeMillis(),
            ownership.serial,
            ownership.operationId,
        )
        return findBySerial(ownership.serial) ?: error("Provisioning v2 ONU authorization was not persisted")
    }

    fun markManagementReady(ownership: ProvisioningV2OnuOwnership): ProvisioningV2OnuOwnership {
        requireOwnership(ownership)
        jdbc.update(
            """
            UPDATE olt_provisioning_v2_onu_operation
            SET stage = 'READY', updated_at_epoch_ms = ?
            WHERE sn = ? AND operation_id = ?
            """.trimIndent(),
            System.currentTimeMillis(),
            ownership.serial,
            ownership.operationId,
        )
        return findBySerial(ownership.serial) ?: error("Provisioning v2 ONU readiness was not persisted")
    }

    fun releaseAfterConfirmedCleanup(ownership: ProvisioningV2OnuOwnership) {
        requireOwnership(ownership)
        jdbc.update(
            "DELETE FROM olt_provisioning_v2_onu_operation WHERE sn = ? AND operation_id = ?",
            ownership.serial,
            ownership.operationId,
        )
    }

    fun requireOwner(serial: String, operationId: String): ProvisioningV2OnuOwnership {
        val ownership = findBySerial(serial) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Provisioning v2 ONU reservation was not found")
        val environment = GatewayCallContext.env()?.trim().orEmpty().ifBlank { "unknown" }
        if (ownership.operationId != operationId || ownership.callerEnv != environment) {
            throw conflict("Provisioning operation does not own this ONU")
        }
        return ownership
    }

    fun fingerprint(parts: List<String>): String {
        val canonical = parts.joinToString("\u001f") { it.trim() }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun requireOwnership(ownership: ProvisioningV2OnuOwnership) {
        val current = requireOwner(ownership.serial, ownership.operationId)
        if (current.requestHash != ownership.requestHash) {
            throw conflict("Provisioning v2 ONU reservation changed unexpectedly")
        }
    }

    private fun findBySerial(serial: String): ProvisioningV2OnuOwnership? = jdbc.query(
        """
        SELECT sn, operation_id, caller_env, request_hash, external_id, board, port, ont_id, stage
        FROM olt_provisioning_v2_onu_operation WHERE sn = ?
        """.trimIndent(),
        ownershipRowMapper,
        serial,
    ).singleOrNull()

    private val ownershipRowMapper = org.springframework.jdbc.core.RowMapper { result, _ ->
        ProvisioningV2OnuOwnership(
            serial = result.getString("sn"),
            operationId = result.getString("operation_id"),
            callerEnv = result.getString("caller_env"),
            requestHash = result.getString("request_hash"),
            externalId = result.getString("external_id"),
            board = result.getObject("board") as Int?,
            port = result.getObject("port") as Int?,
            ontId = result.getObject("ont_id") as Int?,
            stage = result.getString("stage"),
        )
    }

    private fun conflict(message: String) = ResponseStatusException(HttpStatus.CONFLICT, message)
}
