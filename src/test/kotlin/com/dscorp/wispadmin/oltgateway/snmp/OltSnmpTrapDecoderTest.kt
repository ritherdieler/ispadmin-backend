package com.dscorp.wispadmin.oltgateway.snmp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.snmp4j.PDU
import org.snmp4j.mp.SnmpConstants
import org.snmp4j.smi.OID
import org.snmp4j.smi.OctetString
import org.snmp4j.smi.TimeTicks
import org.snmp4j.smi.VariableBinding

class OltSnmpTrapDecoderTest {

    @Test
    fun `decodifica trap v2c linkDown con snmpTrapOID`() {
        val pdu = PDU()
        pdu.type = PDU.TRAP
        pdu.add(VariableBinding(SnmpConstants.sysUpTime, TimeTicks(12345)))
        pdu.add(VariableBinding(SnmpConstants.snmpTrapOID, OID("1.3.6.1.6.3.1.1.5.3")))
        pdu.add(VariableBinding(OID("1.3.6.1.2.1.2.2.1.1.7"), org.snmp4j.smi.Integer32(7)))

        val event = OltSnmpTrapDecoder.decode(
            pdu = pdu,
            sourceHost = "10.11.104.2",
            community = "trap-ro"
        )

        assertEquals("10.11.104.2", event.sourceHost)
        assertEquals("trap-ro", event.community)
        assertEquals("1.3.6.1.6.3.1.1.5.3", event.trapOid)
        assertEquals("linkDown", event.trapLabel)
        assertTrue(event.varbinds.any { it.oid.startsWith("1.3.6.1.2.1.2.2.1.1") })
    }

    @Test
    fun `sin snmpTrapOID deja trapOid null`() {
        val pdu = PDU()
        pdu.type = PDU.TRAP
        pdu.add(VariableBinding(SnmpConstants.sysUpTime, TimeTicks(1)))

        val event = OltSnmpTrapDecoder.decode(pdu, "10.0.0.1", null)
        assertNull(event.trapOid)
        assertNull(event.trapLabel)
        assertEquals("10.0.0.1", event.sourceHost)
    }

    @Test
    fun `etiqueta coldStart conocida`() {
        val pdu = PDU()
        pdu.type = PDU.TRAP
        pdu.add(VariableBinding(SnmpConstants.snmpTrapOID, OID("1.3.6.1.6.3.1.1.5.1")))
        val event = OltSnmpTrapDecoder.decode(pdu, "10.11.104.2", OctetString("x").toString())
        assertEquals("coldStart", event.trapLabel)
    }
}
