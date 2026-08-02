package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncident
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagOltLogEventRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagTargetRepository
import com.dscorp.wispadmin.netdiag.port.NetDiagOntSubscriptionPort
import com.dscorp.wispadmin.netdiag.port.OntSubscriptionInfo
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.time.Instant

data class GponTargetContext(
    val kind: String,
    val oltId: String?,
    val board: Int?,
    val port: Int?,
    val mgmtIp: String?,
    val parentTargetName: String?
)

data class GponOltLogEntry(
    val receivedAt: Instant,
    val reasonCode: String?,
    val onuIndex: Int?,
    val severity: String?,
    val alarmName: String?,
    val isClear: Boolean,
    val rawMessage: String
)

data class PonOnuSummary(
    val onuIndex: Int,
    val sn: String,
    val runState: String?,
    val lastDownCause: String?,
    val onuRxDbm: Double?
)

data class PonInventory(
    val total: Int,
    val online: Int,
    val offline: Int,
    val offlineSample: List<PonOnuSummary>
)

data class OntSubscriptionContext(
    val onuIndex: Int,
    val sn: String?,
    val runState: String?,
    val lastDownCause: String?,
    val subscription: OntSubscriptionInfo?
)

data class GponLlmContext(
    val targetContext: GponTargetContext,
    val recentOltLogs: List<GponOltLogEntry>,
    val ponInventory: PonInventory?,
    val ontSubscription: OntSubscriptionContext?
)

@Service
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
class NetDiagLlmGponContextBuilder(
    private val targetRepository: NetDiagTargetRepository,
    private val oltLogEventRepository: NetDiagOltLogEventRepository,
    private val oltRepository: OltMgrOltRepository,
    private val onuRepository: OltMgrOnuRepository,
    private val ontSubscriptionPort: NetDiagOntSubscriptionPort,
    private val objectMapper: ObjectMapper
) {

    fun build(incident: NetDiagIncident): GponLlmContext? {
        val target = incident.target ?: return null
        val config = parseMonitorConfig(target.monitorConfig)
        val kind = NetDiagMonitorConfigSupport.kind(target.monitorConfig)
        if (kind != NetDiagMonitorConfigSupport.KIND_OLT && kind != NetDiagMonitorConfigSupport.KIND_PON) {
            return null
        }
        val oltId = textField(config, "oltId")
        val board = intField(config, "board")
        val port = intField(config, "port")
        val mgmtIp = textField(config, "mgmtIp")
        val parentTargetName = target.parentTargetId?.let { parentId ->
            targetRepository.findById(parentId).orElse(null)?.name
        }
        val targetContext = GponTargetContext(
            kind = kind,
            oltId = oltId,
            board = board,
            port = port,
            mgmtIp = mgmtIp,
            parentTargetName = parentTargetName
        )
        val targetId = target.id
        val recentOltLogs = targetId?.let { id ->
            oltLogEventRepository.findTop50ByTargetIdOrderByReceivedAtDesc(id)
                .take(MAX_LOG_ENTRIES)
                .map { event ->
                    GponOltLogEntry(
                        receivedAt = event.receivedAt,
                        reasonCode = event.reasonCode,
                        onuIndex = event.onuIndex,
                        severity = event.severity,
                        alarmName = event.alarmName,
                        isClear = event.isClear,
                        rawMessage = truncateRaw(event.rawMessage)
                    )
                }
        }.orEmpty()
        val ponInventory = if (kind == NetDiagMonitorConfigSupport.KIND_PON) {
            buildPonInventory(oltId, board, port)
        } else {
            null
        }
        val ontSubscription = if (kind == NetDiagMonitorConfigSupport.KIND_PON) {
            buildOntSubscription(incident, oltId, board, port)
        } else {
            null
        }
        return GponLlmContext(
            targetContext = targetContext,
            recentOltLogs = recentOltLogs,
            ponInventory = ponInventory,
            ontSubscription = ontSubscription
        )
    }

    private fun buildPonInventory(oltId: String?, board: Int?, port: Int?): PonInventory? {
        if (oltId.isNullOrBlank() || board == null || port == null) {
            return PonInventory(total = 0, online = 0, offline = 0, offlineSample = emptyList())
        }
        val oltPk = oltRepository.findByName(oltId).orElse(null)?.id ?: return PonInventory(
            total = 0,
            online = 0,
            offline = 0,
            offlineSample = emptyList()
        )
        val onus = onuRepository.findByOlt_IdAndBoardAndPortWithStatus(oltPk, board, port)
        val online = onus.count { it.status?.runState.equals("online", ignoreCase = true) }
        val offlineOnus = onus.filter { !it.status?.runState.equals("online", ignoreCase = true) }
        return PonInventory(
            total = onus.size,
            online = online,
            offline = offlineOnus.size,
            offlineSample = offlineOnus
                .sortedBy { it.onuIndex }
                .take(MAX_OFFLINE_SAMPLE)
                .map { toSummary(it) }
        )
    }

    private fun buildOntSubscription(
        incident: NetDiagIncident,
        oltId: String?,
        board: Int?,
        port: Int?
    ): OntSubscriptionContext? {
        val onuIndex = resolveOnuIndex(incident) ?: return null
        if (oltId.isNullOrBlank() || board == null || port == null) {
            return OntSubscriptionContext(
                onuIndex = onuIndex,
                sn = null,
                runState = null,
                lastDownCause = null,
                subscription = null
            )
        }
        val oltPk = oltRepository.findByName(oltId).orElse(null)?.id ?: return OntSubscriptionContext(
            onuIndex = onuIndex,
            sn = null,
            runState = null,
            lastDownCause = null,
            subscription = null
        )
        val onu = onuRepository.findByOlt_IdAndBoardAndPortAndOnuIndexAndDeletedAtIsNull(
            oltPk,
            board,
            port,
            onuIndex
        ).orElse(null)
        val subscription = onu?.sn?.let { ontSubscriptionPort.findActiveByOnuSn(it) }
        return OntSubscriptionContext(
            onuIndex = onuIndex,
            sn = onu?.sn,
            runState = onu?.status?.runState,
            lastDownCause = onu?.status?.lastDownCause,
            subscription = subscription
        )
    }

    private fun resolveOnuIndex(incident: NetDiagIncident): Int? {
        ONU_DEDUP_PATTERN.find(incident.dedupKey)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        ONT_TITLE_PATTERN.find(incident.title)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return null
    }

    private fun toSummary(onu: OltMgrOnu): PonOnuSummary = PonOnuSummary(
        onuIndex = onu.onuIndex,
        sn = onu.sn,
        runState = onu.status?.runState,
        lastDownCause = onu.status?.lastDownCause,
        onuRxDbm = onu.status?.onuRxDbm?.toDouble()
    )

    private fun parseMonitorConfig(raw: String?): JsonNode? {
        if (raw.isNullOrBlank()) return null
        return runCatching { objectMapper.readTree(raw) }.getOrNull()
    }

    private fun textField(config: JsonNode?, field: String): String? {
        val node = config?.get(field) ?: return null
        if (node.isNull) return null
        return node.asText().takeIf { it.isNotBlank() }
    }

    private fun intField(config: JsonNode?, field: String): Int? {
        val node = config?.get(field) ?: return null
        if (node.isNull || !node.canConvertToInt()) return null
        return node.asInt()
    }

    private fun truncateRaw(raw: String): String {
        if (raw.length <= MAX_RAW_CHARS) return raw
        return raw.take(MAX_RAW_CHARS - 1) + "…"
    }

    companion object {
        private const val MAX_LOG_ENTRIES = 20
        private const val MAX_RAW_CHARS = 400
        private const val MAX_OFFLINE_SAMPLE = 10
        private val ONU_DEDUP_PATTERN = Regex("""(?i):ont-(\d+)\s*$""")
        private val ONT_TITLE_PATTERN = Regex("""(?i)ont=(\d+)""")
    }
}
