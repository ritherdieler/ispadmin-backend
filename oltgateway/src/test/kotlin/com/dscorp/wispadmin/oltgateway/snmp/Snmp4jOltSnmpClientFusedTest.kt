package com.dscorp.wispadmin.oltgateway.snmp

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.snmp4j.smi.Integer32
import org.snmp4j.smi.OID
import org.snmp4j.smi.OctetString
import org.snmp4j.smi.Variable
import org.snmp4j.smi.VariableBinding
import java.util.concurrent.atomic.AtomicInteger

class Snmp4jOltSnmpClientFusedTest {

    private val slot = 0
    private val port = 1
    private val emptyPort = 2
    private val ifIndex = HuaweiGponSnmpCodec.encodeIfIndex(slot, port)
    private val emptyIfIndex = HuaweiGponSnmpCodec.encodeIfIndex(slot, emptyPort)
    private val ontId = 5

    private val properties = OltGatewayProperties().apply {
        host = "127.0.0.1"
        snmp.enabled = true
        snmp.roCommunity = "test-ro"
        snmp.maxRepetitions = 25
        snmp.requestIntervalMs = 0
    }


    @Test
    fun `la pasada fusionada pide trece columnas unicas en una sola pagina`() {
        val sender = FusedFakeSender(populated = setOf(ifIndex))
        val client = Snmp4jOltSnmpClient(properties, pageSender = sender)

        client.listInventoryAndOptical(listOf(GponFsp(frame = 0, slot = slot, port = port)))

        val fusedPage = sender.requests.first { it.size > 1 }.map { it.toString() }
        assertEquals(13, fusedPage.size, "fused page must carry 13 columns")
        assertEquals(fusedPage.size, fusedPage.distinct().size, "no duplicated columns")
        val roots = fusedPage.map { it.removeSuffix(".$ifIndex") }
        assertTrue(
            roots.containsAll(
                listOf(
                    HuaweiGponSnmpOids.ONT_SN,
                    HuaweiGponSnmpOids.ONT_RUN_STATUS,
                    HuaweiGponSnmpOids.ONT_MATCH_STATUS,
                    HuaweiGponSnmpOids.ONT_RANGING,
                    HuaweiGponSnmpOids.ONT_LAST_DOWN_CAUSE,
                    HuaweiGponSnmpOids.ONT_DESCRIPTION,
                    HuaweiGponSnmpOids.ONT_LINE_PROF_NAME,
                    HuaweiGponSnmpOids.ONT_SERVICE_PROF_NAME,
                    HuaweiGponSnmpOids.ONT_RX_POWER,
                    HuaweiGponSnmpOids.ONT_TX_POWER,
                    HuaweiGponSnmpOids.OLT_RX_POWER,
                    HuaweiGponSnmpOids.ONT_OPTICAL_TEMPERATURE,
                    HuaweiGponSnmpOids.ONT_OPTICAL_BIAS,
                )
            ),
            "roots=$roots"
        )
        assertTrue(fusedPage.all { it.endsWith(".$ifIndex") }, "fused cursors must be port-scoped")
    }

    @Test
    fun `una pasada fusionada devuelve inventario y optica del mismo GETBULK`() {
        val sender = FusedFakeSender(populated = setOf(ifIndex))
        val client = Snmp4jOltSnmpClient(properties, pageSender = sender)

        val snapshot = client.listInventoryAndOptical(listOf(GponFsp(frame = 0, slot = slot, port = port)))

        assertEquals(1, sender.fusedPagesWithData, "inventory and optical must share one page")

        val onu = snapshot.onus.single()
        assertEquals(slot, onu.slot)
        assertEquals(port, onu.port)
        assertEquals(ontId, onu.ontId)
        assertEquals("VSOL0086F6E9", onu.sn)
        assertEquals("online", onu.runState)
        assertEquals("match", onu.matchState)
        assertEquals(1234, onu.distanceM)
        assertEquals("pwr", onu.lastDownCause)
        assertEquals("casa-1", onu.description)
        assertEquals("LINE-100", onu.lineProfileName)
        assertEquals("SRV-100", onu.serviceProfileName)

        val optical = snapshot.optical.single()
        assertEquals(SnmpOntKey(ifIndex = ifIndex, ontId = ontId), optical.key)
        assertEquals(-25.1, optical.onuRxDbm)
        assertEquals(2.1, optical.onuTxDbm)
        assertEquals(-27.45, optical.oltRxDbm!!, 1e-9)
        assertEquals(45.0, optical.temperatureC)
        assertEquals(12.0, optical.biasCurrentMa)
        assertEquals(1234, optical.distanceM)
        assertEquals("match", optical.matchState)

        assertEquals(1, snapshot.portsAttempted)
        assertEquals(0, snapshot.portsFailed)
    }

    @Test
    fun `un puerto sin ONTs se descarta con un probe de una sola columna`() {
        val sender = FusedFakeSender(populated = setOf(ifIndex))
        val client = Snmp4jOltSnmpClient(properties, pageSender = sender)

        val snapshot = client.listInventoryAndOptical(
            listOf(
                GponFsp(frame = 0, slot = slot, port = emptyPort),
                GponFsp(frame = 0, slot = slot, port = port),
            )
        )

        val emptyProbes = sender.requests.filter { req ->
            req.size == 1 && req.single().toString().endsWith(".$emptyIfIndex")
        }
        assertEquals(1, emptyProbes.size, "empty port must be probed exactly once")
        assertTrue(
            sender.requests.none { it.size > 1 && it.first().toString().endsWith(".$emptyIfIndex") },
            "empty port must not run the 13-column walk"
        )
        assertEquals(2, snapshot.portsAttempted)
        assertEquals(0, snapshot.portsFailed)
        assertEquals(1, snapshot.onus.size)
        assertEquals(1, snapshot.optical.size)
    }

    @Test
    fun `el probe barato usa una columna de la tabla de configuracion`() {
        val sender = FusedFakeSender(populated = setOf(ifIndex))
        val client = Snmp4jOltSnmpClient(properties, pageSender = sender)

        client.listInventoryAndOptical(listOf(GponFsp(frame = 0, slot = slot, port = port)))

        val probe = sender.requests.first()
        assertEquals(1, probe.size, "port probe must be a single varbind")
        assertEquals("${HuaweiGponSnmpOids.ONT_RUN_STATUS}.$ifIndex", probe.single().toString())
        assertFalse(
            probe.single().toString().startsWith(HuaweiGponSnmpOids.ONT_RX_POWER),
            "probe must not touch the DDM table"
        )
    }

    @Test
    fun `fused es serial aunque opticalParallelPorts sea 3 y PER_PORT false`() {
        properties.snmp.opticalParallelPorts = 3
        properties.snmp.opticalPerPortWalks = false
        properties.snmp.fusedInventoryOptical = true
        val sender = ConcurrentTrackingSender(populated = setOf(ifIndex, emptyIfIndex))
        val client = Snmp4jOltSnmpClient(properties, pageSender = sender)

        client.listInventoryAndOptical(
            listOf(
                GponFsp(frame = 0, slot = slot, port = port),
                GponFsp(frame = 0, slot = slot, port = emptyPort),
                GponFsp(frame = 0, slot = 0, port = 3),
            )
        )

        assertEquals(1, sender.maxConcurrent.get(), "fused walk must ignore opticalParallelPorts")
    }

    @Test
    fun `un GETBULK vacio en el probe falla el puerto en vez de saltarlo`() {
        val sender = EmptyPduOnProbeSender(populated = setOf(ifIndex), emptyIfIndex = emptyIfIndex)
        val client = Snmp4jOltSnmpClient(properties, pageSender = sender)

        val snapshot = client.listInventoryAndOptical(
            listOf(
                GponFsp(frame = 0, slot = slot, port = emptyPort),
                GponFsp(frame = 0, slot = slot, port = port),
            )
        )

        assertEquals(2, snapshot.portsAttempted)
        assertEquals(1, snapshot.portsFailed, "empty PDU must fail the port, not skip it")
        assertEquals(1, snapshot.onus.size)
    }

    private class FusedFakeSender(
        private val populated: Set<Long>,
        private val ontId: Int = 5,
    ) : SnmpGetBulkPageSender {

        val requests = mutableListOf<List<OID>>()
        var fusedPagesWithData = 0
            private set

        private val servedFused = mutableSetOf<Long>()
        private val servedProbe = mutableSetOf<Long>()

        override fun send(type: SnmpJobType, cursors: List<OID>, maxRepetitions: Int): List<VariableBinding> {
            requests += cursors
            val ifIndex = scopeOf(cursors.first()) ?: return outOfSubtree(cursors)
            if (ifIndex !in populated) return outOfSubtree(cursors)
            val served = if (cursors.size == 1) servedProbe else servedFused
            if (!served.add(ifIndex)) return outOfSubtree(cursors)
            if (cursors.size > 1) fusedPagesWithData++
            return cursors.map { cursor ->
                VariableBinding(OID("$cursor.$ontId"), valueFor(cursor.toString()))
            }
        }

        private fun scopeOf(cursor: OID): Long? {
            val parts = cursor.toString().split(".")
            return parts.lastOrNull()?.toLongOrNull()?.takeIf { it > 1_000_000L }
        }

        private fun outOfSubtree(cursors: List<OID>): List<VariableBinding> =
            cursors.map { VariableBinding(OID(OUT_OF_SUBTREE), Integer32(0)) }

        private fun valueFor(cursor: String): Variable = when {
            cursor.startsWith(HuaweiGponSnmpOids.ONT_SN) -> ontSn()
            cursor.startsWith(HuaweiGponSnmpOids.ONT_DESCRIPTION) -> OctetString("casa-1")
            cursor.startsWith(HuaweiGponSnmpOids.ONT_LINE_PROF_NAME) -> OctetString("LINE-100")
            cursor.startsWith(HuaweiGponSnmpOids.ONT_SERVICE_PROF_NAME) -> OctetString("SRV-100")
            cursor.startsWith(HuaweiGponSnmpOids.ONT_RUN_STATUS) -> Integer32(1)
            cursor.startsWith(HuaweiGponSnmpOids.ONT_MATCH_STATUS) -> Integer32(1)
            cursor.startsWith(HuaweiGponSnmpOids.ONT_RANGING) -> Integer32(1234)
            cursor.startsWith(HuaweiGponSnmpOids.ONT_LAST_DOWN_CAUSE) -> Integer32(13)
            cursor.startsWith(HuaweiGponSnmpOids.ONT_RX_POWER) -> Integer32(-2510)
            cursor.startsWith(HuaweiGponSnmpOids.ONT_TX_POWER) -> Integer32(210)
            cursor.startsWith(HuaweiGponSnmpOids.OLT_RX_POWER) -> Integer32(7255)
            cursor.startsWith(HuaweiGponSnmpOids.ONT_OPTICAL_TEMPERATURE) -> Integer32(45)
            cursor.startsWith(HuaweiGponSnmpOids.ONT_OPTICAL_BIAS) -> Integer32(12000)
            else -> Integer32(0)
        }

        private fun ontSn(): OctetString {
            val bytes = byteArrayOf(
                'V'.code.toByte(), 'S'.code.toByte(), 'O'.code.toByte(), 'L'.code.toByte(),
                0x00, 0x86.toByte(), 0xF6.toByte(), 0xE9.toByte(),
            )
            return OctetString(bytes)
        }

        private companion object {
            const val OUT_OF_SUBTREE = "1.3.6.1.4.1.2011.6.128.1.1.2.99.1.1.1"
        }
    }

    private class ConcurrentTrackingSender(
        populated: Set<Long>,
    ) : SnmpGetBulkPageSender {
        private val inner = FusedFakeSender(populated)
        private val inFlight = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)

        override fun send(type: SnmpJobType, cursors: List<OID>, maxRepetitions: Int): List<VariableBinding> {
            val now = inFlight.incrementAndGet()
            maxConcurrent.updateAndGet { maxOf(it, now) }
            try {
                Thread.sleep(40)
                return inner.send(type, cursors, maxRepetitions)
            } finally {
                inFlight.decrementAndGet()
            }
        }
    }

    private class EmptyPduOnProbeSender(
        populated: Set<Long>,
        private val emptyIfIndex: Long,
    ) : SnmpGetBulkPageSender {
        private val inner = FusedFakeSender(populated)

        override fun send(type: SnmpJobType, cursors: List<OID>, maxRepetitions: Int): List<VariableBinding> {
            val cursor = cursors.singleOrNull()?.toString() ?: return inner.send(type, cursors, maxRepetitions)
            if (cursors.size == 1 && cursor.endsWith(".$emptyIfIndex")) {
                return emptyList()
            }
            return inner.send(type, cursors, maxRepetitions)
        }
    }
}
