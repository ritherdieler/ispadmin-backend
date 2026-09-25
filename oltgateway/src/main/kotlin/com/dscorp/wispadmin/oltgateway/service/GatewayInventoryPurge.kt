package com.dscorp.wispadmin.oltgateway.service

import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate

class GatewayInventoryPurge(private val jdbc: JdbcTemplate) {
    fun purge(sn: String) {
        val serial = sn.trim()
        val deleted = "$serial#del#%"
        statements.forEach { sql ->
            try {
                jdbc.update(sql, serial, deleted)
            } catch (ex: DataAccessException) {
                if (!missingTable(ex)) throw ex
            }
        }
    }

    private fun missingTable(ex: DataAccessException): Boolean {
        val message = ex.mostSpecificCause.message.orEmpty()
        return message.contains("doesn't exist") || message.contains("does not exist")
    }

    private companion object {
        val statements = listOf(
            "UPDATE olt_mgr_task SET onu_id = NULL WHERE onu_id IN (SELECT id FROM (SELECT id FROM olt_mgr_onu WHERE sn = ? OR sn LIKE ?) t)",
            "UPDATE olt_mgr_audit_log SET onu_id = NULL WHERE onu_id IN (SELECT id FROM (SELECT id FROM olt_mgr_onu WHERE sn = ? OR sn LIKE ?) t)",
            "DELETE FROM olt_mgr_onu_status_current WHERE onu_id IN (SELECT id FROM (SELECT id FROM olt_mgr_onu WHERE sn = ? OR sn LIKE ?) t)",
            "DELETE FROM olt_mgr_onu_service_port WHERE onu_id IN (SELECT id FROM (SELECT id FROM olt_mgr_onu WHERE sn = ? OR sn LIKE ?) t)",
            "DELETE FROM olt_mgr_onu_extra_vlan WHERE onu_id IN (SELECT id FROM (SELECT id FROM olt_mgr_onu WHERE sn = ? OR sn LIKE ?) t)",
            "DELETE FROM olt_mgr_onu WHERE sn = ? OR sn LIKE ?",
            "DELETE FROM olt_activation_operation WHERE UPPER(sn) = UPPER(?) OR sn LIKE ?",
            "DELETE FROM olt_provisioning_v2_onu_operation WHERE UPPER(sn) = UPPER(?) OR sn LIKE ?",
        )
    }
}
