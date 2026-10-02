package com.dscorp.wispadmin.acs.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

data class CpeFaultHistoryItem(
    val id: String,
    val deviceId: String,
    val sn: String,
    val channel: String?,
    val taskId: String?,
    val code: String,
    val description: String,
    val parameters: List<String>,
    val retries: Int?,
    val occurredAt: String?,
    val firstSeenAt: String,
    val lastSeenAt: String,
    val resolvedAt: String?,
)

data class CpeFaultHistoryPage(
    val items: List<CpeFaultHistoryItem>,
    val page: Int,
    val size: Int,
    val total: Long,
)

class AcsFaultArchive(private val jdbc: JdbcTemplate, private val tx: TransactionTemplate? = null) {
    private val json = ObjectMapper()

    fun captureKnownDevice(deviceId: String, faults: List<CpeFaultView>, at: Instant = Instant.now()) {
        val serial = jdbc.queryForList("SELECT sn FROM cpe_record WHERE device_id=?", String::class.java, deviceId)
            .singleOrNull() ?: return
        capture(deviceId, serial, faults, at)
    }

    fun capture(deviceId: String, sn: String, faults: List<CpeFaultView>, at: Instant = Instant.now()) {
        if (tx == null) captureRows(deviceId, sn, faults, at)
        else tx.executeWithoutResult { captureRows(deviceId, sn, faults, at) }
    }

    private fun captureRows(deviceId: String, sn: String, faults: List<CpeFaultView>, at: Instant) {
        val stamp = at.toString()
        val seen = faults.map { it.id }.toSet()
        for (fault in faults) {
            require(fault.deviceId == deviceId) { "Fault belongs to a different device" }
            val parameters = json.writeValueAsString(fault.parameters)
            val updated = jdbc.update(
                """UPDATE acs_fault_history SET channel=?, task_id=?, code=?, description=?, parameters_json=?,
                    retries=?, occurred_at=?, last_seen_at=?, resolved_at=NULL WHERE device_id=? AND fault_id=?""",
                fault.channel, fault.taskId, fault.code, fault.description, parameters,
                fault.retries, fault.occurredAt, stamp, deviceId, fault.id,
            )
            if (updated == 0) {
                try {
                    jdbc.update(
                        """INSERT INTO acs_fault_history
                          (device_id, fault_id, sn, channel, task_id, code, description, parameters_json,
                           retries, occurred_at, first_seen_at, last_seen_at)
                          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                        deviceId, fault.id, sn, fault.channel, fault.taskId, fault.code, fault.description,
                        parameters, fault.retries, fault.occurredAt, stamp, stamp,
                    )
                } catch (_: DuplicateKeyException) {
                    jdbc.update(
                        "UPDATE acs_fault_history SET last_seen_at=?, retries=?, resolved_at=NULL WHERE device_id=? AND fault_id=?",
                        stamp, fault.retries, deviceId, fault.id,
                    )
                }
            }
        }
        val activeIds = jdbc.queryForList(
            "SELECT fault_id FROM acs_fault_history WHERE device_id=? AND resolved_at IS NULL", String::class.java, deviceId,
        )
        for (id in activeIds) {
            if (id !in seen) {
                jdbc.update("UPDATE acs_fault_history SET resolved_at=? WHERE device_id=? AND fault_id=? AND resolved_at IS NULL",
                    stamp, deviceId, id)
            }
        }
    }

    fun page(deviceId: String, page: Int, size: Int): CpeFaultHistoryPage {
        require(page >= 0 && size in 1..100) { "Invalid pagination" }
        val total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM acs_fault_history WHERE device_id=?", Long::class.java, deviceId,
        )
        val items = jdbc.query(
            """SELECT device_id, fault_id, sn, channel, task_id, code, description, parameters_json,
                retries, occurred_at, first_seen_at, last_seen_at, resolved_at
                FROM acs_fault_history WHERE device_id=? ORDER BY first_seen_at DESC, fault_id DESC LIMIT ? OFFSET ?""",
            { rs, _ ->
                CpeFaultHistoryItem(
                    id = rs.getString("fault_id"), deviceId = rs.getString("device_id"), sn = rs.getString("sn"),
                    channel = rs.getString("channel"), taskId = rs.getString("task_id"),
                    code = rs.getString("code"), description = rs.getString("description"),
                    parameters = json.readTree(rs.getString("parameters_json")).map { it.asText() },
                    retries = rs.getInt("retries").takeUnless { rs.wasNull() },
                    occurredAt = rs.getString("occurred_at"), firstSeenAt = rs.getString("first_seen_at"),
                    lastSeenAt = rs.getString("last_seen_at"), resolvedAt = rs.getString("resolved_at"),
                )
            }, deviceId, size, page * size,
        )
        return CpeFaultHistoryPage(items, page, size, total)
    }
}
