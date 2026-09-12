package com.dscorp.wispadmin.oltgateway.snmp

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OltSnmpJobTimeoutsTest {

    private val snmp = OltGatewayProperties().snmp.apply {
        timeoutMs = 20_000
        retries = 2
        inventoryTimeoutMs = 5_000
        inventoryRetries = 4
    }

    @Test
    fun `las tablas de configuracion usan el timeout corto`() {
        assertEquals(OltSnmpJobTimeouts.Budget(5_000, 4), OltSnmpJobTimeouts.forJob(SnmpJobType.INVENTORY, snmp))
        assertEquals(OltSnmpJobTimeouts.Budget(5_000, 4), OltSnmpJobTimeouts.forJob(SnmpJobType.AUTOFIND, snmp))
    }

    @Test
    fun `la tabla DDM conserva el timeout largo`() {
        assertEquals(OltSnmpJobTimeouts.Budget(20_000, 2), OltSnmpJobTimeouts.forJob(SnmpJobType.OPTICAL, snmp))
        assertEquals(OltSnmpJobTimeouts.Budget(20_000, 2), OltSnmpJobTimeouts.forJob(SnmpJobType.FUSED, snmp))
        assertEquals(OltSnmpJobTimeouts.Budget(20_000, 2), OltSnmpJobTimeouts.forJob(SnmpJobType.PROBE, snmp))
    }

    @Test
    fun `un timeout de inventario no configurado cae al general`() {
        snmp.inventoryTimeoutMs = 0
        snmp.inventoryRetries = 0

        assertEquals(OltSnmpJobTimeouts.Budget(20_000, 2), OltSnmpJobTimeouts.forJob(SnmpJobType.INVENTORY, snmp))
    }

    @Test
    fun `el presupuesto total del inventario no supera al de la optica`() {
        val inventory = OltSnmpJobTimeouts.forJob(SnmpJobType.INVENTORY, snmp)
        val optical = OltSnmpJobTimeouts.forJob(SnmpJobType.OPTICAL, snmp)

        assertEquals(true, inventory.worstCaseMs() < optical.worstCaseMs())
    }
}
