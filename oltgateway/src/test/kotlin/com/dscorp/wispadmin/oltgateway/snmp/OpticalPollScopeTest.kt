package com.dscorp.wispadmin.oltgateway.snmp

import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOlt
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuStatusCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpticalPollScopeTest {

    private val olt = OltMgrOlt(id = 1L, name = "test-olt", ipAddress = "10.0.0.1")

    @Test
    fun `onlineOnly excluye puertos offline y soft-deleted`() {
        val online = onu(1L, 0, 1, "online")
        val offline = onu(2L, 0, 2, "offline")
        val deleted = onu(3L, 1, 0, "online").apply { deletedAt = java.time.Instant.now() }

        val ports = OpticalPollScope.portsFromOnus(listOf(online, offline, deleted), onlineOnly = true)

        assertEquals(1, ports.size)
        assertEquals(GponFsp(0, 0, 1), ports.single())
    }

    @Test
    fun `onlineOnly false incluye offline activos`() {
        val offline = onu(2L, 1, 3, "offline")
        val ports = OpticalPollScope.portsFromOnus(listOf(offline), onlineOnly = false)
        assertEquals(setOf(GponFsp(0, 1, 3)), ports)
    }

    @Test
    fun `deduplica puertos con varias ONUs`() {
        val a = onu(1L, 0, 4, "online", ontId = 0)
        val b = onu(2L, 0, 4, "online", ontId = 1)
        val ports = OpticalPollScope.portsFromOnus(listOf(a, b), onlineOnly = true)
        assertEquals(1, ports.size)
        assertTrue(ports.contains(GponFsp(0, 0, 4)))
    }

    @Test
    fun `fusedScanPorts barre todos los puertos de cada placa con ONUs`() {
        val onus = listOf(onu(1L, 0, 3, "online"), onu(2L, 1, 0, "offline"))

        val ports = OpticalPollScope.fusedScanPorts(onus, portsPerBoard = 16)

        assertEquals(32, ports.size)
        assertTrue(ports.contains(GponFsp(0, 0, 0)))
        assertTrue(ports.contains(GponFsp(0, 0, 15)))
        assertTrue(ports.contains(GponFsp(0, 1, 15)))
    }

    @Test
    fun `fusedScanPorts ignora ONUs borradas y no filtra por runState`() {
        val deleted = onu(3L, 2, 0, "online").apply { deletedAt = java.time.Instant.now() }
        val offline = onu(4L, 0, 1, "offline")

        val ports = OpticalPollScope.fusedScanPorts(listOf(deleted, offline), portsPerBoard = 8)

        assertEquals(8, ports.size)
        assertTrue(ports.none { it.slot == 2 })
        assertTrue(ports.contains(GponFsp(0, 0, 7)))
    }

    private fun onu(id: Long, board: Int, port: Int, runState: String, ontId: Int = 0): OltMgrOnu {
        val onu = OltMgrOnu(
            id = id,
            sn = "SN$id",
            externalId = "ext_$id",
            olt = olt,
            board = board,
            port = port,
            onuIndex = ontId
        )
        onu.status = OltMgrOnuStatusCurrent(onu = onu, runState = runState)
        return onu
    }
}
