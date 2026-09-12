package com.dscorp.wispadmin.oltgateway.snmp

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties

/**
 * Per-pass UDP budget. The MA5608T drops GETBULK pages silently at a rate independent of the
 * table, but a config-table page answers in ~100 ms while a DDM page takes seconds: waiting the
 * DDM timeout on a dropped inventory page is what turned a 6 s walk into 137 s.
 */
object OltSnmpJobTimeouts {

    data class Budget(val timeoutMs: Long, val retries: Int) {
        fun worstCaseMs(): Long = timeoutMs * (retries + 1)
    }

    fun forJob(type: SnmpJobType, snmp: OltGatewayProperties.SnmpProperties): Budget {
        val general = Budget(snmp.timeoutMs, snmp.retries)
        return when (type) {
            SnmpJobType.INVENTORY, SnmpJobType.AUTOFIND -> {
                if (snmp.inventoryTimeoutMs <= 0) general
                else Budget(snmp.inventoryTimeoutMs, snmp.inventoryRetries.coerceAtLeast(0))
            }
            SnmpJobType.PROBE, SnmpJobType.OPTICAL, SnmpJobType.FUSED -> general
        }
    }
}
