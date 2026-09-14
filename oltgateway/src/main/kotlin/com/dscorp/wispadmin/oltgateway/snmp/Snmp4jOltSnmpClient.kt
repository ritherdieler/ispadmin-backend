package com.dscorp.wispadmin.oltgateway.snmp

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.parser.ParsedAutofindOnt
import com.dscorp.wispadmin.oltgateway.parser.ParsedOnuSummary
import com.dscorp.wispadmin.oltgateway.ssh.LocalCliBusPressure
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
import org.snmp4j.smi.SMIConstants
import org.snmp4j.smi.UdpAddress
import org.snmp4j.smi.VariableBinding
import org.snmp4j.transport.DefaultUdpTransportMapping
import java.io.IOException

@Suppress("UNCHECKED_CAST")
class Snmp4jOltSnmpClient(
    private val properties: OltGatewayProperties,
    private val busRegistry: OltSnmpBusRegistry? = null,
    private val localCliBusPressure: () -> LocalCliBusPressure = { LocalCliBusPressure.snapshot(null) },
    private val pageSender: SnmpGetBulkPageSender? = null
) : OltSnmpClient {

    companion object {
        private val logger = LoggerFactory.getLogger(Snmp4jOltSnmpClient::class.java)

        private val INVENTORY_COLUMNS = listOf(
            HuaweiGponSnmpOids.ONT_SN,
            HuaweiGponSnmpOids.ONT_RUN_STATUS,
            HuaweiGponSnmpOids.ONT_MATCH_STATUS,
            HuaweiGponSnmpOids.ONT_RANGING,
            HuaweiGponSnmpOids.ONT_LAST_DOWN_CAUSE,
            HuaweiGponSnmpOids.ONT_DESCRIPTION,
            HuaweiGponSnmpOids.ONT_LINE_PROF_NAME,
            HuaweiGponSnmpOids.ONT_SERVICE_PROF_NAME
        )

        private val OPTICAL_COLUMNS = listOf(
            HuaweiGponSnmpOids.ONT_RX_POWER,
            HuaweiGponSnmpOids.ONT_TX_POWER,
            HuaweiGponSnmpOids.OLT_RX_POWER,
            HuaweiGponSnmpOids.ONT_OPTICAL_TEMPERATURE,
            HuaweiGponSnmpOids.ONT_OPTICAL_BIAS,
            HuaweiGponSnmpOids.ONT_RANGING,
            HuaweiGponSnmpOids.ONT_MATCH_STATUS
        )

        /**
         * Inventory + optical minus the two columns both sets share (ranging, match state):
         * 8 + 7 - 2 = 13 unique columns. Measured on the MA5608T: the 8 config columns ride
         * inside the DDM page for +3% (280.8 vs 290.0 ms per ONT, interleaved A/B).
         */
        private val FUSED_COLUMNS = INVENTORY_COLUMNS + listOf(
            HuaweiGponSnmpOids.ONT_RX_POWER,
            HuaweiGponSnmpOids.ONT_TX_POWER,
            HuaweiGponSnmpOids.OLT_RX_POWER,
            HuaweiGponSnmpOids.ONT_OPTICAL_TEMPERATURE,
            HuaweiGponSnmpOids.ONT_OPTICAL_BIAS
        )

        private const val INVENTORY_MATCH_STATUS = 2
        private const val INVENTORY_RANGING = 3
        private const val FUSED_RX = 8
        private const val FUSED_TX = 9
        private const val FUSED_OLT_RX = 10
        private const val FUSED_TEMPERATURE = 11
        private const val FUSED_BIAS = 12
    }

    private data class PortFused(
        val onus: List<ParsedOnuSummary>,
        val optical: List<SnmpOntOptical>
    )

    override fun probeSysObjectId(): String? {
        return getOid(OID("1.3.6.1.2.1.1.2.0"))?.toString()
    }

    override fun listConfiguredOnus(): List<ParsedOnuSummary> {
        return decodeInventory(walkColumns(INVENTORY_COLUMNS, SnmpJobType.INVENTORY))
    }

    override fun listInventoryAndOptical(ports: Collection<GponFsp>): OltSnmpFusedSnapshot {
        val portList = ports.distinct()
        if (portList.isEmpty()) {
            lastPortsFailed = 0
            lastPortsAttempted = 0
            return OltSnmpFusedSnapshot(emptyList(), emptyList(), 0, 0)
        }
        val batch = OpticalPortWalkRunner.runAll(
            ports = portList,
            parallelism = 1,
            pressureSnapshot = localCliBusPressure,
            fetch = { port -> listOf(fetchFusedForPort(port)) }
        )
        lastPortsFailed = batch.portsFailed
        lastPortsAttempted = batch.portsAttempted
        return OltSnmpFusedSnapshot(
            onus = batch.items.flatMap { it.onus }
                .sortedWith(compareBy({ it.slot }, { it.port }, { it.ontId })),
            optical = batch.items.flatMap { it.optical },
            portsAttempted = batch.portsAttempted,
            portsFailed = batch.portsFailed
        )
    }

    private fun fetchFusedForPort(port: GponFsp): PortFused {
        val ifIndex = HuaweiGponSnmpCodec.encodeIfIndex(port.slot, port.port)
        return withPageSender(SnmpJobType.FUSED) { send ->
            if (!portHasOnts(send, ifIndex)) {
                logger.debug("SNMP fused skip empty port {}/{}", port.slot, port.port)
                return@withPageSender PortFused(emptyList(), emptyList())
            }
            val columns = walkColumnsWith(send, FUSED_COLUMNS, SnmpJobType.FUSED, ifIndex)
            PortFused(
                onus = decodeInventory(columns),
                optical = mergeOptical(
                    listOf(
                        columns[FUSED_RX],
                        columns[FUSED_TX],
                        columns[FUSED_OLT_RX],
                        columns[FUSED_TEMPERATURE],
                        columns[FUSED_BIAS],
                        columns[INVENTORY_RANGING],
                        columns[INVENTORY_MATCH_STATUS]
                    )
                )
            )
        }
    }

    /**
     * One page on a config-table column: an empty GPON port answers in ~50 ms there, while the
     * same probe inside the 13-column DDM page costs ~2.8 s (measured over the 10 empty ports).
     */
    private fun portHasOnts(send: (List<OID>, String) -> List<VariableBinding>, ifIndex: Long): Boolean {
        val root = OID("${HuaweiGponSnmpOids.ONT_RUN_STATUS}.$ifIndex")
        val bindings = send(listOf(root), "fused/probe/$ifIndex")
        if (bindings.isEmpty()) {
            throw IOException("SNMP fused probe empty PDU for ifIndex=$ifIndex")
        }
        val hasOnts = bindings.any { binding ->
            val oid = binding.oid
            oid != null && !binding.variable.isException && oid.startsWith(root)
        }
        if (hasOnts) {
            return true
        }
        val agentSaidEmpty = bindings.any { binding ->
            val oid = binding.oid
            binding.variable.syntax == SMIConstants.EXCEPTION_END_OF_MIB_VIEW ||
                (oid != null && !oid.startsWith(root))
        }
        if (agentSaidEmpty) {
            return false
        }
        throw IOException("SNMP fused probe inconclusive for ifIndex=$ifIndex")
    }

    private fun decodeInventory(columns: List<Map<SnmpOntKey, VariableBinding>>): List<ParsedOnuSummary> {
        val snByKey = columns[0].decode { decodeSn(it) }
        val statusByKey = columns[1].decode { HuaweiGponSnmpCodec.decodeRunState(it.variable.toInt()) }
        val matchByKey = columns[2].decode { HuaweiGponSnmpCodec.decodeMatchState(it.variable.toInt()) }
        val distanceByKey = columns[3].decode { HuaweiGponSnmpCodec.decodeRangingMeters(it.variable.toInt()) }
        val lastDownByKey = columns[4].decode { HuaweiGponSnmpCodec.decodeLastDownCause(it.variable.toInt()) }
        val descriptionByKey = columns[5].decode { decodeDisplayString(it) }
        val lineProfByKey = columns[6].decode { decodeDisplayString(it) }
        val srvProfByKey = columns[7].decode { decodeDisplayString(it) }
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
        return walkColumns(listOf(HuaweiGponSnmpOids.AUTOFIND_SN), SnmpJobType.AUTOFIND)[0]
            .decode { decodeSn(it) }
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
        val batch = OpticalPortWalkRunner.runAll(
            ports = ports,
            parallelism = properties.snmp.opticalParallelPorts,
            pressureSnapshot = localCliBusPressure,
            fetch = { port -> fetchOpticalForPort(port) }
        )
        lastPortsFailed = batch.portsFailed
        lastPortsAttempted = batch.portsAttempted
        return batch.items
    }

    @Volatile
    private var lastPortsFailed: Int = 0

    @Volatile
    private var lastPortsAttempted: Int = 0

    override fun lastOpticalWalkPortsFailed(): Int = lastPortsFailed

    override fun lastOpticalWalkPortsAttempted(): Int = lastPortsAttempted

    private fun fetchOpticalForPort(port: GponFsp): List<SnmpOntOptical> {
        val ifIndex = HuaweiGponSnmpCodec.encodeIfIndex(port.slot, port.port)
        val merged = mergeOptical(walkColumns(OPTICAL_COLUMNS, SnmpJobType.OPTICAL, ifIndex))
        logger.debug("SNMP optical merged scope={}/{} rows={}", port.slot, port.port, merged.size)
        return merged
    }

    private fun fetchOpticalColumns(label: String): List<SnmpOntOptical> {
        lastPortsFailed = 0
        lastPortsAttempted = 0
        val merged = mergeOptical(walkColumns(OPTICAL_COLUMNS, SnmpJobType.OPTICAL))
        if (merged.isEmpty()) {
            throw IOException("SNMP optical $label: all columns empty")
        }
        logger.debug("SNMP optical merged scope={} rows={}", label, merged.size)
        return merged
    }

    private fun mergeOptical(columns: List<Map<SnmpOntKey, VariableBinding>>): List<SnmpOntOptical> {
        return SnmpOpticalMerger.merge(
            rx = columns[0].decode { HuaweiGponSnmpCodec.decodeOntPowerDbm(it.variable.toInt()) },
            tx = columns[1].decode { HuaweiGponSnmpCodec.decodeOntPowerDbm(it.variable.toInt()) },
            oltRx = columns[2].decode { HuaweiGponSnmpCodec.decodeOltRxPowerDbm(it.variable.toInt()) },
            temperatureC = columns[3].decode { HuaweiGponSnmpCodec.decodeTemperatureC(it.variable.toInt()) },
            biasCurrentMa = columns[4].decode { HuaweiGponSnmpCodec.decodeBiasCurrentMa(it.variable.toInt()) },
            distanceM = columns[5].decode { HuaweiGponSnmpCodec.decodeRangingMeters(it.variable.toInt()) },
            matchState = columns[6].decode { HuaweiGponSnmpCodec.decodeMatchState(it.variable.toInt()) }
        )
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

    /**
     * One bounded multi-varbind GETBULK per page instead of [TreeUtils] and instead of one
     * walk per column: on the MA5608T the agent bills one OMCI read per ONT and serves every
     * column from it, so 7-8 columns in the same PDU cost the same as one. Bounded pages also
     * keep an intermittently dropped response from parking the per-OLT SNMP bus forever.
     */
    private fun walkColumns(
        columnOids: List<String>,
        type: SnmpJobType,
        ifIndex: Long? = null
    ): List<Map<SnmpOntKey, VariableBinding>> {
        return withPageSender(type) { send -> walkColumnsWith(send, columnOids, type, ifIndex) }
    }

    private fun walkColumnsWith(
        send: (List<OID>, String) -> List<VariableBinding>,
        columnOids: List<String>,
        type: SnmpJobType,
        ifIndex: Long?
    ): List<Map<SnmpOntKey, VariableBinding>> {
        val roots = columnOids.map { OID(if (ifIndex == null) it else "$it.$ifIndex") }
        val scope = ifIndex?.toString() ?: "full"
        val label = "${type.name.lowercase()}/${columnOids.size}col/$scope"
        val columns = SnmpMultiColumnWalk(
            sendPage = { cursors -> send(cursors, label) },
            betweenPages = ::pacePages
        ).walk(roots, label)
        val rowsByColumn = columns.mapIndexed { index, bindings ->
            val root = roots[index]
            val rows = linkedMapOf<SnmpOntKey, VariableBinding>()
            for (binding in bindings) {
                val key = if (ifIndex == null) {
                    parseOntKey(root, binding.oid)
                } else {
                    parseOntKeyForPort(root, binding.oid, ifIndex)
                } ?: continue
                rows[key] = binding
            }
            rows
        }
        logger.debug("SNMP walk {} rows={}", label, rowsByColumn.sumOf { it.size })
        return rowsByColumn
    }

    private fun <T> Map<SnmpOntKey, VariableBinding>.decode(map: (VariableBinding) -> T?): Map<SnmpOntKey, T> {
        val decoded = linkedMapOf<SnmpOntKey, T>()
        for ((key, binding) in this) {
            val value = map(binding) ?: continue
            decoded[key] = value
        }
        return decoded
    }

    private fun pacePages() {
        val interval = properties.snmp.requestIntervalMs
        if (interval <= 0) return
        try {
            Thread.sleep(interval)
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("SNMP walk interrupted between pages", ex)
        }
    }

    private fun <T> withPageSender(
        type: SnmpJobType,
        block: (send: (List<OID>, String) -> List<VariableBinding>) -> T
    ): T {
        val injected = pageSender
        if (injected != null) {
            return block { cursors, _ -> injected.send(type, cursors, properties.snmp.maxRepetitions) }
        }
        return withSnmp(type) { snmp, target ->
            block { cursors, label -> sendGetBulkPage(snmp, target, cursors, label) }
        }
    }

    private fun <T> withSnmp(type: SnmpJobType, block: (Snmp, CommunityTarget) -> T): T {
        val bus = busRegistry?.forOlt(properties.oltId, properties.modelCode)
        val budget = OltSnmpJobTimeouts.forJob(type, properties.snmp)
        return if (bus != null) {
            bus.acquire(type) { openSnmpSession(budget, block) }
        } else {
            openSnmpSession(budget, block)
        }
    }

    private fun sendGetBulkPage(
        snmp: Snmp,
        target: CommunityTarget,
        cursors: List<OID>,
        label: String
    ): List<VariableBinding> {
        val request = PDU().apply {
            this.type = PDU.GETBULK
            nonRepeaters = 0
            maxRepetitions = properties.snmp.maxRepetitions
            cursors.forEach { add(VariableBinding(it)) }
        }
        val response = snmp.send(request, target).response
            ?: throw IOException("SNMP walk error on $label: request timed out")
        if (response.errorStatus != PDU.noError) {
            throw IOException("SNMP walk error on $label: ${response.errorStatusText}")
        }
        return (0 until response.size()).mapNotNull { response.get(it) }
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

    private fun <T> openSnmpSession(
        budget: OltSnmpJobTimeouts.Budget,
        block: (Snmp, CommunityTarget) -> T
    ): T {
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
            target.timeout = budget.timeoutMs
            target.retries = budget.retries
            return block(snmp, target)
        } finally {
            try {
                snmp.close()
            } catch (_: Exception) {
            }
        }
    }
}
