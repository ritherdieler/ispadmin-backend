package com.dscorp.wispadmin.oltgateway.snmp

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.snmp4j.CommunityTarget
import org.snmp4j.PDU
import org.snmp4j.Snmp
import org.snmp4j.mp.SnmpConstants
import org.snmp4j.smi.GenericAddress
import org.snmp4j.smi.OID
import org.snmp4j.smi.OctetString
import org.snmp4j.smi.TimeTicks
import org.snmp4j.smi.UdpAddress
import org.snmp4j.smi.VariableBinding
import org.snmp4j.transport.DefaultUdpTransportMapping
import java.net.DatagramSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OltSnmpTrapReceiverTest {

    @Test
    fun `recibe trap v2c en puerto efimero`() {
        val port = freeUdpPort()
        val props = OltGatewayProperties().apply {
            snmp.trap.enabled = true
            snmp.trap.listenPort = port
            snmp.trap.bindAddress = "127.0.0.1"
            snmp.trap.community = ""
            snmp.trap.bufferSize = 10
            snmp.trap.dispatcherThreads = 1
        }
        val buffer = RecentOltSnmpTrapBuffer(10)
        val latch = CountDownLatch(1)
        val receiver = OltSnmpTrapReceiver(props, buffer) { latch.countDown() }
        receiver.start()
        try {
            Thread.sleep(100)
            sendV2cTrap(port)
            assertTrue(latch.await(5, TimeUnit.SECONDS), "trap not received")
            val recent = buffer.recent()
            assertEquals(1, recent.size)
            assertEquals("1.3.6.1.6.3.1.1.5.3", recent[0].trapOid)
            assertEquals("linkDown", recent[0].trapLabel)
        } finally {
            receiver.close()
        }
    }

    private fun freeUdpPort(): Int {
        DatagramSocket().use { return it.localPort }
    }

    private fun sendV2cTrap(port: Int) {
        val transport = DefaultUdpTransportMapping()
        transport.listen()
        val snmp = Snmp(transport)
        try {
            val pdu = PDU()
            pdu.type = PDU.TRAP
            pdu.add(VariableBinding(SnmpConstants.sysUpTime, TimeTicks(100)))
            pdu.add(VariableBinding(SnmpConstants.snmpTrapOID, OID("1.3.6.1.6.3.1.1.5.3")))
            val target = CommunityTarget()
            target.community = OctetString("public")
            target.version = SnmpConstants.version2c
            target.address = GenericAddress.parse("udp:127.0.0.1/$port") as UdpAddress
            target.timeout = 1000
            target.retries = 0
            snmp.send(pdu, target)
        } finally {
            try {
                snmp.close()
            } catch (_: Exception) {
            }
        }
    }
}
