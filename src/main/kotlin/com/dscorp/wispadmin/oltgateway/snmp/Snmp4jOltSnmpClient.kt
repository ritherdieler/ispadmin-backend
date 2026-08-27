package com.dscorp.wispadmin.oltgateway.snmp

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.parser.ParsedAutofindOnt
import com.dscorp.wispadmin.oltgateway.parser.ParsedOnuSummary
import org.slf4j.LoggerFactory
import org.snmp4j.CommunityTarget
import org.snmp4j.PDU
import org.snmp4j.Snmp
import org.snmp4j.TransportMapping
import org.snmp4j.event.ResponseEvent
import org.snmp4j.mp.SnmpConstants
import org.snmp4j.smi.Address
import org.snmp4j.smi.GenericAddress
import org.snmp4j.smi.OID
import org.snmp4j.smi.OctetString
import org.snmp4j.smi.UdpAddress
import org.snmp4j.smi.VariableBinding
import org.snmp4j.transport.DefaultUdpTransportMapping
import org.snmp4j.util.DefaultPDUFactory
import org.snmp4j.util.TreeEvent
import org.snmp4j.util.TreeUtils
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

@Suppress("UNCHECKED_CAST")
class Snmp4jOltSnmpClient(
    private val properties: OltGatewayProperties,
    private val busRegistry: OltSnmpBusRegistry? = null
) : OltSnmpClient {

    companion object {
        private val logger = LoggerFactory.getLogger(Snmp4jOltSnmpClient::class.java)
    }

    override fun probeSysObjectId(): String? {
        return getOid(OID("1.3.6.1.2.1.1.2.0"))?.toString()
    }

    override fun listConfiguredOnus(): List<ParsedOnuSummary> {
        val snByKey = walkColumn(HuaweiGponSnmpOids.ONT_SN, SnmpJobType.INVENTORY) { vb ->
            decodeSn(vb)
        }
        val statusByKey = walkColumn(HuaweiGponSnmpOids.ONT_RUN_STATUS, SnmpJobType.INVENTORY) { vb ->
            HuaweiGponSnmpCodec.decodeRunState(vb.variable.toInt())
        }
        return snByKey.mapNotNull { (key, sn) ->
            val fsp = HuaweiGponSnmpCodec.decodeIfIndex(key.ifIndex)
            ParsedOnuSummary(
                frame = fsp.frame,
                slot = fsp.slot,
                port = fsp.port,
                ontId = key.ontId,
                sn = sn,
                runState = statusByKey[key]
            )
        }.sortedWith(compareBy({ it.slot }, { it.port }, { it.ontId }))
    }

    override fun listAutofind(): List<ParsedAutofindOnt> {
        return walkColumn(HuaweiGponSnmpOids.AUTOFIND_SN, SnmpJobType.AUTOFIND) { vb -> decodeSn(vb) }
            .map { (key, sn) ->
                val fsp = HuaweiGponSnmpCodec.decodeIfIndex(key.ifIndex)
                ParsedAutofindOnt(
                    sn = sn,
                    frame = fsp.frame,
                    slot = fsp.slot,
                    port = fsp.port
                )
            }
            .sortedWith(compareBy({ it.slot }, { it.port }, { it.sn }))
    }

    override fun listOptical(ports: Collection<GponFsp>?): List<SnmpOntOptical> {
        val portList = ports?.distinct()?.takeIf { it.isNotEmpty() }
        return if (portList == null) {
            fetchOpticalColumns(
                label = "full",
                rx = {
                    walkColumn(HuaweiGponSnmpOids.ONT_RX_POWER, SnmpJobType.OPTICAL) { vb ->
                        HuaweiGponSnmpCodec.decodeOntPowerDbm(vb.variable.toInt())
                    }
                },
                tx = {
                    walkColumn(HuaweiGponSnmpOids.ONT_TX_POWER, SnmpJobType.OPTICAL) { vb ->
                        HuaweiGponSnmpCodec.decodeOntPowerDbm(vb.variable.toInt())
                    }
                },
                oltRx = {
                    walkColumn(HuaweiGponSnmpOids.OLT_RX_POWER, SnmpJobType.OPTICAL) { vb ->
                        HuaweiGponSnmpCodec.decodeOltRxPowerDbm(vb.variable.toInt())
                    }
                }
            )
        } else {
            fetchOpticalForPorts(portList)
        }
    }

    private fun fetchOpticalForPorts(ports: List<GponFsp>): List<SnmpOntOptical> {
        return OpticalPortWalkRunner.runAll(
            ports = ports,
            parallelism = properties.snmp.opticalParallelPorts,
            fetch = { port -> fetchOpticalForPort(port) }
        )
    }

    private fun fetchOpticalForPort(port: GponFsp): List<SnmpOntOptical> {
        val ifIndex = HuaweiGponSnmpCodec.encodeIfIndex(port.slot, port.port)
        // Per-port walks are small; sequential columns avoid overloading the OLT agent.
        val rx = walkColumnForIfIndex(HuaweiGponSnmpOids.ONT_RX_POWER, ifIndex, SnmpJobType.OPTICAL) { vb ->
            HuaweiGponSnmpCodec.decodeOntPowerDbm(vb.variable.toInt())
        }
        val tx = walkColumnForIfIndex(HuaweiGponSnmpOids.ONT_TX_POWER, ifIndex, SnmpJobType.OPTICAL) { vb ->
            HuaweiGponSnmpCodec.decodeOntPowerDbm(vb.variable.toInt())
        }
        val oltRx = walkColumnForIfIndex(HuaweiGponSnmpOids.OLT_RX_POWER, ifIndex, SnmpJobType.OPTICAL) { vb ->
            HuaweiGponSnmpCodec.decodeOltRxPowerDbm(vb.variable.toInt())
        }
        val merged = SnmpOpticalMerger.merge(rx, tx, oltRx)
        logger.debug("SNMP optical merged scope={}/{} rows={}", port.slot, port.port, merged.size)
        return merged
    }

    private fun fetchOpticalColumns(
        label: String,
        rx: () -> Map<SnmpOntKey, Double?>,
        tx: () -> Map<SnmpOntKey, Double?>,
        oltRx: () -> Map<SnmpOntKey, Double?>
    ): List<SnmpOntOptical> {
        val rxSafe = { safeColumn("rx", rx) }
        val txSafe = { safeColumn("tx", tx) }
        val oltSafe = { safeColumn("oltRx", oltRx) }
        val merged = if (!properties.snmp.opticalParallelColumns) {
            SnmpOpticalMerger.merge(rxSafe(), txSafe(), oltSafe())
        } else {
            val executor = Executors.newFixedThreadPool(3)
            try {
                val rxFuture = executor.submit<Map<SnmpOntKey, Double?>> { rxSafe() }
                val txFuture = executor.submit<Map<SnmpOntKey, Double?>> { txSafe() }
                val oltFuture = executor.submit<Map<SnmpOntKey, Double?>> { oltSafe() }
                SnmpOpticalMerger.merge(rxFuture.get(), txFuture.get(), oltFuture.get())
            } catch (ex: ExecutionException) {
                val cause = ex.cause
                if (cause is IOException) throw cause
                throw IOException("SNMP optical column walk failed: ${cause?.message}", cause)
            } finally {
                executor.shutdown()
            }
        }
        if (merged.isEmpty()) {
            throw IOException("SNMP optical $label: all columns empty")
        }
        logger.debug("SNMP optical merged scope={} rows={}", label, merged.size)
        return merged
    }

    private fun safeColumn(
        name: String,
        walk: () -> Map<SnmpOntKey, Double?>
    ): Map<SnmpOntKey, Double?> {
        return try {
            walk()
        } catch (ex: Exception) {
            logger.warn("SNMP optical column {} failed: {}", name, ex.message)
            emptyMap()
        }
    }

    private fun decodeSn(vb: VariableBinding): String? {
        val variable = vb.variable
        return if (variable is OctetString) {
            val bytes = variable.value
            if (bytes.size < 8) null else HuaweiGponSnmpCodec.decodeOntSn(bytes)
        } else {
            null
        }
    }

    private fun <T> walkColumn(
        columnOid: String,
        type: SnmpJobType,
        map: (VariableBinding) -> T?
    ): Map<SnmpOntKey, T> {
        val root = OID(columnOid)
        val result = linkedMapOf<SnmpOntKey, T>()
        withSnmp(type) { snmp, target ->
            val treeUtils = TreeUtils(snmp, DefaultPDUFactory())
            treeUtils.maxRepetitions = properties.snmp.maxRepetitions
            @Suppress("UNCHECKED_CAST")
            val events = treeUtils.getSubtree(target, root) as List<TreeEvent>
            for (event in events) {
                if (event.isError) {
                    throw IOException("SNMP walk error on $columnOid: ${event.errorMessage}")
                }
                val vbs = event.variableBindings ?: continue
                for (vb in vbs) {
                    val oid = vb.oid ?: continue
                    if (!oid.startsWith(root)) continue
                    val key = parseOntKey(root, oid) ?: continue
                    val mapped = map(vb) ?: continue
                    result[key] = mapped
                }
            }
        }
        logger.debug("SNMP walk {} rows={}", columnOid, result.size)
        return result
    }

    private fun <T> walkColumnForIfIndex(
        columnOid: String,
        ifIndex: Long,
        type: SnmpJobType,
        map: (VariableBinding) -> T?
    ): Map<SnmpOntKey, T> {
        val root = OID("$columnOid.$ifIndex")
        val result = linkedMapOf<SnmpOntKey, T>()
        withSnmp(type) { snmp, target ->
            val treeUtils = TreeUtils(snmp, DefaultPDUFactory())
            treeUtils.maxRepetitions = properties.snmp.maxRepetitions
            @Suppress("UNCHECKED_CAST")
            val events = treeUtils.getSubtree(target, root) as List<TreeEvent>
            for (event in events) {
                if (event.isError) {
                    throw IOException("SNMP walk error on $columnOid.$ifIndex: ${event.errorMessage}")
                }
                val vbs = event.variableBindings ?: continue
                for (vb in vbs) {
                    val oid = vb.oid ?: continue
                    if (!oid.startsWith(root)) continue
                    val key = parseOntKeyForPort(root, oid, ifIndex) ?: continue
                    val mapped = map(vb) ?: continue
                    result[key] = mapped
                }
            }
        }
        logger.debug("SNMP walk {}.{} rows={}", columnOid, ifIndex, result.size)
        return result
    }

    private fun parseOntKeyForPort(columnRoot: OID, oid: OID, ifIndex: Long): SnmpOntKey? {
        if (oid.size() < columnRoot.size() + 1) return null
        val ontId = oid.get(oid.size() - 1)
        return SnmpOntKey(ifIndex = ifIndex, ontId = ontId)
    }

    private fun parseOntKey(columnRoot: OID, oid: OID): SnmpOntKey? {
        if (oid.size() < columnRoot.size() + 2) return null
        // SNMP subids are unsigned 32-bit; snmp4j OID.get returns signed int.
        val ifIndex = oid.get(oid.size() - 2).toLong() and 0xFFFFFFFFL
        val ontId = oid.get(oid.size() - 1)
        return SnmpOntKey(ifIndex = ifIndex, ontId = ontId)
    }

    private fun getOid(oid: OID): OID? {
        return withSnmp(SnmpJobType.PROBE) { snmp, target ->
            val pdu = PDU()
            pdu.type = PDU.GET
            pdu.add(VariableBinding(oid))
            val event: ResponseEvent? = snmp.send(pdu, target)
            val response = event?.response ?: return@withSnmp null
            if (response.errorStatus != PDU.noError) return@withSnmp null
            val vb = response.get(0) ?: return@withSnmp null
            vb.variable as? OID
        }
    }

    private fun <T> withSnmp(type: SnmpJobType, block: (Snmp, CommunityTarget) -> T): T {
        val bus = busRegistry?.forOlt(properties.oltId, properties.modelCode)
        return if (bus != null) {
            bus.acquire(type) { openSnmpSession(block) }
        } else {
            openSnmpSession(block)
        }
    }

    private fun <T> openSnmpSession(block: (Snmp, CommunityTarget) -> T): T {
        val snmpProps = properties.snmp
        require(snmpProps.roCommunity.isNotBlank()) { "olt.gateway.snmp.ro-community is blank" }
        val address = GenericAddress.parse("udp:${properties.host}/${snmpProps.port}") as Address
        val transport = DefaultUdpTransportMapping()
        transport.listen()
        val snmp = Snmp(transport as TransportMapping<UdpAddress>)
        try {
            val target = CommunityTarget()
            target.community = OctetString(snmpProps.roCommunity)
            target.address = address
            target.version = SnmpConstants.version2c
            target.timeout = snmpProps.timeoutMs
            target.retries = snmpProps.retries
            return block(snmp, target)
        } finally {
            try {
                snmp.close()
            } catch (_: Exception) {
            }
        }
    }
}
