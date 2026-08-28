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
        val matchByKey = walkColumn(HuaweiGponSnmpOids.ONT_MATCH_STATUS, SnmpJobType.INVENTORY) { vb ->
            HuaweiGponSnmpCodec.decodeMatchState(vb.variable.toInt())
        }
        val distanceByKey = walkColumn(HuaweiGponSnmpOids.ONT_RANGING, SnmpJobType.INVENTORY) { vb ->
            HuaweiGponSnmpCodec.decodeRangingMeters(vb.variable.toInt())
        }
        val lastDownByKey = walkColumn(HuaweiGponSnmpOids.ONT_LAST_DOWN_CAUSE, SnmpJobType.INVENTORY) { vb ->
            HuaweiGponSnmpCodec.decodeLastDownCause(vb.variable.toInt())
        }
        val descriptionByKey = walkColumn(HuaweiGponSnmpOids.ONT_DESCRIPTION, SnmpJobType.INVENTORY) { vb ->
            decodeDisplayString(vb)
        }
        val lineProfByKey = walkColumn(HuaweiGponSnmpOids.ONT_LINE_PROF_NAME, SnmpJobType.INVENTORY) { vb ->
            decodeDisplayString(vb)
        }
        val srvProfByKey = walkColumn(HuaweiGponSnmpOids.ONT_SERVICE_PROF_NAME, SnmpJobType.INVENTORY) { vb ->
            decodeDisplayString(vb)
        }
        return snByKey.mapNotNull { (key, sn) ->
            val fsp = HuaweiGponSnmpCodec.decodeIfIndex(key.ifIndex)
            ParsedOnuSummary(
                frame = fsp.frame,
                slot = fsp.slot,
                port = fsp.port,
                ontId = key.ontId,
                sn = sn,
                runState = statusByKey[key],
                matchState = matchByKey[key],
                description = descriptionByKey[key],
                distanceM = distanceByKey[key],
                lastDownCause = lastDownByKey[key],
                lineProfileName = lineProfByKey[key],
                serviceProfileName = srvProfByKey[key]
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
            fetchOpticalColumns(label = "full")
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
        val rx = walkColumnForIfIndex(HuaweiGponSnmpOids.ONT_RX_POWER, ifIndex, SnmpJobType.OPTICAL) { vb ->
            HuaweiGponSnmpCodec.decodeOntPowerDbm(vb.variable.toInt())
        }
        val tx = walkColumnForIfIndex(HuaweiGponSnmpOids.ONT_TX_POWER, ifIndex, SnmpJobType.OPTICAL) { vb ->
            HuaweiGponSnmpCodec.decodeOntPowerDbm(vb.variable.toInt())
        }
        val oltRx = walkColumnForIfIndex(HuaweiGponSnmpOids.OLT_RX_POWER, ifIndex, SnmpJobType.OPTICAL) { vb ->
            HuaweiGponSnmpCodec.decodeOltRxPowerDbm(vb.variable.toInt())
        }
        val temperatureC = walkColumnForIfIndex(HuaweiGponSnmpOids.ONT_OPTICAL_TEMPERATURE, ifIndex, SnmpJobType.OPTICAL) { vb ->
            HuaweiGponSnmpCodec.decodeTemperatureC(vb.variable.toInt())
        }
        val biasCurrentMa = walkColumnForIfIndex(HuaweiGponSnmpOids.ONT_OPTICAL_BIAS, ifIndex, SnmpJobType.OPTICAL) { vb ->
            HuaweiGponSnmpCodec.decodeBiasCurrentMa(vb.variable.toInt())
        }
        val distanceM = walkColumnForIfIndex(HuaweiGponSnmpOids.ONT_RANGING, ifIndex, SnmpJobType.OPTICAL) { vb ->
            HuaweiGponSnmpCodec.decodeRangingMeters(vb.variable.toInt())
        }
        val matchState = walkColumnForIfIndex(HuaweiGponSnmpOids.ONT_MATCH_STATUS, ifIndex, SnmpJobType.OPTICAL) { vb ->
            HuaweiGponSnmpCodec.decodeMatchState(vb.variable.toInt())
        }
        val merged = SnmpOpticalMerger.merge(
            rx = rx,
            tx = tx,
            oltRx = oltRx,
            temperatureC = temperatureC,
            biasCurrentMa = biasCurrentMa,
            distanceM = distanceM,
            matchState = matchState
        )
        logger.debug("SNMP optical merged scope={}/{} rows={}", port.slot, port.port, merged.size)
        return merged
    }

    private fun fetchOpticalColumns(label: String): List<SnmpOntOptical> {
        val rx = { safeDoubleColumn("rx") {
            walkColumn(HuaweiGponSnmpOids.ONT_RX_POWER, SnmpJobType.OPTICAL) { vb ->
                HuaweiGponSnmpCodec.decodeOntPowerDbm(vb.variable.toInt())
            }
        } }
        val tx = { safeDoubleColumn("tx") {
            walkColumn(HuaweiGponSnmpOids.ONT_TX_POWER, SnmpJobType.OPTICAL) { vb ->
                HuaweiGponSnmpCodec.decodeOntPowerDbm(vb.variable.toInt())
            }
        } }
        val oltRx = { safeDoubleColumn("oltRx") {
            walkColumn(HuaweiGponSnmpOids.OLT_RX_POWER, SnmpJobType.OPTICAL) { vb ->
                HuaweiGponSnmpCodec.decodeOltRxPowerDbm(vb.variable.toInt())
            }
        } }
        val temperatureC = { safeDoubleColumn("temperatureC") {
            walkColumn(HuaweiGponSnmpOids.ONT_OPTICAL_TEMPERATURE, SnmpJobType.OPTICAL) { vb ->
                HuaweiGponSnmpCodec.decodeTemperatureC(vb.variable.toInt())
            }
        } }
        val biasCurrentMa = { safeDoubleColumn("biasCurrentMa") {
            walkColumn(HuaweiGponSnmpOids.ONT_OPTICAL_BIAS, SnmpJobType.OPTICAL) { vb ->
                HuaweiGponSnmpCodec.decodeBiasCurrentMa(vb.variable.toInt())
            }
        } }
        val distanceM = { safeIntColumn("distanceM") {
            walkColumn(HuaweiGponSnmpOids.ONT_RANGING, SnmpJobType.OPTICAL) { vb ->
                HuaweiGponSnmpCodec.decodeRangingMeters(vb.variable.toInt())
            }
        } }
        val matchState = { safeStringColumn("matchState") {
            walkColumn(HuaweiGponSnmpOids.ONT_MATCH_STATUS, SnmpJobType.OPTICAL) { vb ->
                HuaweiGponSnmpCodec.decodeMatchState(vb.variable.toInt())
            }
        } }

        val merged = if (!properties.snmp.opticalParallelColumns) {
            SnmpOpticalMerger.merge(
                rx = rx(),
                tx = tx(),
                oltRx = oltRx(),
                temperatureC = temperatureC(),
                biasCurrentMa = biasCurrentMa(),
                distanceM = distanceM(),
                matchState = matchState()
            )
        } else {
            val executor = Executors.newFixedThreadPool(7)
            try {
                val rxFuture = executor.submit<Map<SnmpOntKey, Double?>> { rx() }
                val txFuture = executor.submit<Map<SnmpOntKey, Double?>> { tx() }
                val oltFuture = executor.submit<Map<SnmpOntKey, Double?>> { oltRx() }
                val tempFuture = executor.submit<Map<SnmpOntKey, Double?>> { temperatureC() }
                val biasFuture = executor.submit<Map<SnmpOntKey, Double?>> { biasCurrentMa() }
                val distFuture = executor.submit<Map<SnmpOntKey, Int?>> { distanceM() }
                val matchFuture = executor.submit<Map<SnmpOntKey, String?>> { matchState() }
                SnmpOpticalMerger.merge(
                    rx = rxFuture.get(),
                    tx = txFuture.get(),
                    oltRx = oltFuture.get(),
                    temperatureC = tempFuture.get(),
                    biasCurrentMa = biasFuture.get(),
                    distanceM = distFuture.get(),
                    matchState = matchFuture.get()
                )
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

    private fun safeDoubleColumn(
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

    private fun safeIntColumn(
        name: String,
        walk: () -> Map<SnmpOntKey, Int?>
    ): Map<SnmpOntKey, Int?> {
        return try {
            walk()
        } catch (ex: Exception) {
            logger.warn("SNMP optical column {} failed: {}", name, ex.message)
            emptyMap()
        }
    }

    private fun safeStringColumn(
        name: String,
        walk: () -> Map<SnmpOntKey, String?>
    ): Map<SnmpOntKey, String?> {
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

    private fun decodeDisplayString(vb: VariableBinding): String? {
        val variable = vb.variable
        val text = when (variable) {
            is OctetString -> variable.toString()
            else -> variable.toString()
        }.trim()
        return text.takeIf { it.isNotEmpty() && it != "NULL" }
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
