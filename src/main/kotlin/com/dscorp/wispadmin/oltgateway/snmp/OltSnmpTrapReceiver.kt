package com.dscorp.wispadmin.oltgateway.snmp

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import org.slf4j.LoggerFactory
import org.snmp4j.CommandResponder
import org.snmp4j.CommandResponderEvent
import org.snmp4j.MessageDispatcherImpl
import org.snmp4j.Snmp
import org.snmp4j.TransportMapping
import org.snmp4j.mp.MPv1
import org.snmp4j.mp.MPv2c
import org.snmp4j.security.SecurityProtocols
import org.snmp4j.smi.Address
import org.snmp4j.smi.GenericAddress
import org.snmp4j.smi.OctetString
import org.snmp4j.smi.UdpAddress
import org.snmp4j.transport.DefaultUdpTransportMapping
import org.snmp4j.util.MultiThreadedMessageDispatcher
import org.snmp4j.util.ThreadPool
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SNMPv2c trap/notification receiver for Huawei OLT (ASN.1).
 * Does not share NetDiag MikroTik UDP :1620 (text payload).
 */
class OltSnmpTrapReceiver(
    private val properties: OltGatewayProperties,
    private val buffer: RecentOltSnmpTrapBuffer,
    private val onTrap: (OltSnmpTrapEvent) -> Unit = {}
) : CommandResponder, Closeable {

    companion object {
        private val logger = LoggerFactory.getLogger(OltSnmpTrapReceiver::class.java)
    }

    private val running = AtomicBoolean(false)
    private var threadPool: ThreadPool? = null
    private var snmp: Snmp? = null

    fun start() {
        if (!properties.snmp.trap.enabled) {
            logger.info("OLT SNMP trap receiver disabled")
            return
        }
        if (!running.compareAndSet(false, true)) {
            return
        }
        val bind = properties.snmp.trap.bindAddress.ifBlank { "0.0.0.0" }
        val port = properties.snmp.trap.listenPort
        val listenAddress = GenericAddress.parse("udp:$bind/$port") as Address
        val transport = DefaultUdpTransportMapping(listenAddress as UdpAddress)
        val pool = ThreadPool.create("olt-snmp-trap", properties.snmp.trap.dispatcherThreads.coerceAtLeast(1))
        threadPool = pool
        val dispatcher = MultiThreadedMessageDispatcher(pool, MessageDispatcherImpl())
        dispatcher.addMessageProcessingModel(MPv1())
        dispatcher.addMessageProcessingModel(MPv2c())
        SecurityProtocols.getInstance().addDefaultProtocols()
        val session = Snmp(dispatcher, transport as TransportMapping<*>)
        session.addCommandResponder(this)
        session.listen()
        snmp = session
        logger.info("OLT SNMP trap receiver listening on udp:{}/{}", bind, port)
    }

    override fun processPdu(event: CommandResponderEvent?) {
        if (event == null) return
        val pdu = event.pdu ?: return
        val peer = event.peerAddress?.toString()
        val sourceHost = peer?.substringBefore("/")?.removePrefix("udp:")
        val community = try {
            OctetString(event.securityName).toString()
        } catch (_: Exception) {
            null
        }
        val expected = properties.snmp.trap.community
        if (expected.isNotBlank() && community != null && community != expected) {
            logger.debug("Ignoring SNMP trap with unmatched community from {}", sourceHost)
            return
        }
        val decoded = OltSnmpTrapDecoder.decode(pdu, sourceHost, community)
        buffer.accept(decoded)
        onTrap(decoded)
        logger.info(
            "OLT SNMP trap from={} oid={} label={} varbinds={}",
            decoded.sourceHost,
            decoded.trapOid,
            decoded.trapLabel,
            decoded.varbinds.size
        )
        event.isProcessed = true
    }

    override fun close() {
        if (!running.compareAndSet(true, false)) {
            return
        }
        try {
            snmp?.close()
        } catch (ex: IOException) {
            logger.debug("SNMP trap receiver close: {}", ex.message)
        } finally {
            snmp = null
            threadPool?.stop()
            threadPool = null
        }
        logger.info("OLT SNMP trap receiver stopped")
    }
}
