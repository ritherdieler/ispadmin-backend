package com.dscorp.wispadmin.oltgateway.snmp

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.snmp4j.smi.Integer32
import org.snmp4j.smi.OID
import org.snmp4j.smi.OctetString
import org.snmp4j.smi.Variable
import org.snmp4j.smi.VariableBinding

class Snmp4jOltSnmpClientMultiColumnTest {

    private val slot = 0
    private val port = 1
    private val ifIndex = HuaweiGponSnmpCodec.encodeIfIndex(slot, port)
    private val ontId = 5

    private val properties = OltGatewayProperties().apply {
        host = "127.0.0.1"
        snmp.enabled = true
        snmp.roCommunity = "test-ro"
        snmp.maxRepetitions = 25
        snmp.requestIntervalMs = 0
    }

    @Test
    fun `OID conserva ifIndex sin signo`() {
        assertEquals("$ifIndex.$ontId", OID("$ifIndex.$ontId").toString())
    }

    @Test
    fun `listConfiguredOnus pide las ocho columnas en un unico GETBULK`() {
        val sender = FakeGetBulkSender(ontIds = listOf(ontId)) { cursor, id -> "$cursor.$ifIndex.$id" }
        val client = Snmp4jOltSnmpClient(properties, pageSender = sender)

        val onus = client.listConfiguredOnus()

        assertEquals(1, sender.pagesWithData, "columns must share one GETBULK page")
        assertEquals(
            listOf(
                HuaweiGponSnmpOids.ONT_SN,
                HuaweiGponSnmpOids.ONT_RUN_STATUS,
                HuaweiGponSnmpOids.ONT_MATCH_STATUS,
                HuaweiGponSnmpOids.ONT_RANGING,
                HuaweiGponSnmpOids.ONT_LAST_DOWN_CAUSE,
                HuaweiGponSnmpOids.ONT_DESCRIPTION,
                HuaweiGponSnmpOids.ONT_LINE_PROF_NAME,
                HuaweiGponSnmpOids.ONT_SERVICE_PROF_NAME,
            ),
            sender.requests.first().map { it.toString() }
        )
        assertEquals(listOf(SnmpJobType.INVENTORY), sender.types.distinct())
        assertEquals(25, sender.maxRepetitions.first())

        val onu = onus.single()
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
    }

    @Test
    fun `optica por puerto pide las siete columnas scoped al ifIndex en un GETBULK`() {
        val sender = FakeGetBulkSender(ontIds = listOf(ontId)) { cursor, id -> "$cursor.$id" }
        val client = Snmp4jOltSnmpClient(properties, pageSender = sender)

        val optical = client.listOptical(listOf(GponFsp(frame = 0, slot = slot, port = port)))

        assertEquals(1, sender.pagesWithData)
        val cursors = sender.requests.first().map { it.toString() }
        assertEquals(7, cursors.size)
        assertTrue(cursors.all { it.endsWith(".$ifIndex") }, "cursors=$cursors")
        assertEquals(listOf(SnmpJobType.OPTICAL), sender.types.distinct())

        val row = optical.single()
        assertEquals(SnmpOntKey(ifIndex = ifIndex, ontId = ontId), row.key)
        assertEquals(-25.1, row.onuRxDbm)
        assertEquals(2.1, row.onuTxDbm)
        assertEquals(-27.45, row.oltRxDbm!!, 1e-9)
        assertEquals(45.0, row.temperatureC)
        assertEquals(12.0, row.biasCurrentMa)
        assertEquals(1234, row.distanceM)
        assertEquals("match", row.matchState)
        assertEquals(1, client.lastOpticalWalkPortsAttempted())
        assertEquals(0, client.lastOpticalWalkPortsFailed())
    }

    @Test
    fun `optica full-table pide las siete columnas en un GETBULK`() {
        val sender = FakeGetBulkSender(ontIds = listOf(ontId)) { cursor, id -> "$cursor.$ifIndex.$id" }
        val client = Snmp4jOltSnmpClient(properties, pageSender = sender)

        val optical = client.listOptical(null)

        assertEquals(1, sender.pagesWithData)
        assertEquals(7, sender.requests.first().size)
        assertTrue(
            sender.requests.first().none { it.toString().endsWith(".$ifIndex") },
            "full-table cursors must not be scoped"
        )
        assertEquals(SnmpOntKey(ifIndex = ifIndex, ontId = ontId), optical.single().key)
        assertEquals(-25.1, optical.single().onuRxDbm)
    }

    @Test
    fun `listAutofind usa el walk multi con una sola columna`() {
        val sender = FakeGetBulkSender(ontIds = listOf(ontId)) { cursor, id -> "$cursor.$ifIndex.$id" }
        val client = Snmp4jOltSnmpClient(properties, pageSender = sender)

        val autofind = client.listAutofind()

        assertEquals(listOf(HuaweiGponSnmpOids.AUTOFIND_SN), sender.requests.first().map { it.toString() })
        assertEquals(listOf(SnmpJobType.AUTOFIND), sender.types.distinct())
        val found = autofind.single()
        assertEquals("VSOL0086F6E9", found.sn)
        assertEquals(slot, found.slot)
        assertEquals(port, found.port)
    }

    private class FakeGetBulkSender(
        private val ontIds: List<Int>,
        private val oidFor: (cursor: String, ontId: Int) -> String,
    ) : SnmpGetBulkPageSender {

        val requests = mutableListOf<List<OID>>()
        val types = mutableListOf<SnmpJobType>()
        val maxRepetitions = mutableListOf<Int>()
        var pagesWithData = 0
            private set

        private var served = false

        override fun send(type: SnmpJobType, cursors: List<OID>, maxRepetitions: Int): List<VariableBinding> {
            requests += cursors
            types += type
            this.maxRepetitions += maxRepetitions
            if (served) {
                return cursors.map { VariableBinding(OID(OUT_OF_SUBTREE), Integer32(0)) }
            }
            served = true
            pagesWithData++
            return ontIds.flatMap { id ->
                cursors.map { cursor ->
                    VariableBinding(OID(oidFor(cursor.toString(), id)), valueFor(cursor.toString()))
                }
            }
        }

        private fun valueFor(cursor: String): Variable = when {
            cursor.startsWith(HuaweiGponSnmpOids.ONT_SN) -> ontSn()
            cursor.startsWith(HuaweiGponSnmpOids.AUTOFIND_SN) -> ontSn()
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
}
