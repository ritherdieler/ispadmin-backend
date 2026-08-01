package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagOltLogEvent
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagOltLogEventRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagTargetRepository
import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.parser.HuaweiOltAlarmParser
import com.dscorp.wispadmin.oltgateway.parser.ParsedOltAlarm
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

data class OltAlarmIngestResult(
    val persisted: Int,
    val parsed: Int,
    val unparsed: Int,
    val alertsEmitted: Int = 0,
    val cleared: Int = 0,
    val openedIncidentIds: List<Long> = emptyList(),
    val eventIds: List<Long> = emptyList()
)

@Service
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
class OltAlarmIngestService(
    private val logEventRepository: NetDiagOltLogEventRepository,
    private val parser: HuaweiOltAlarmParser,
    private val targetRepository: NetDiagTargetRepository,
    private val incidentRepository: NetDiagIncidentRepository,
    private val signalExtractor: AlertSignalExtractor,
    private val alertEvaluator: AlertEvaluator,
    private val oltGatewayProperties: OltGatewayProperties
) {

    private val oltId: String
        get() = oltGatewayProperties.oltId

    @Transactional
    fun ingestCliActiveAlarms(
        raw: String,
        sourceIp: String?,
        oltTargetId: Long?,
        channel: String = "cli_alarm_active"
    ): OltAlarmIngestResult {
        if (raw.isBlank()) {
            return OltAlarmIngestResult(0, 0, 0)
        }
        val alarms = parser.parseActiveAlarms(raw)
        val now = Instant.now()
        val activeDedupKeys = mutableSetOf<String>()
        var alertsEmitted = 0
        var cleared = 0
        val opened = mutableListOf<Long>()
        val events = mutableListOf<NetDiagOltLogEvent>()

        for (alarm in alarms) {
            val targetId = resolveTargetId(alarm, oltTargetId)
            val event = toEvent(alarm, sourceIp, targetId, channel, now)
            val component = alertComponent(alarm)
            val dedupKey = buildDedupKey(alarm.reasonCode, targetId, component)

            if (alarm.isClear) {
                if (shouldEmitAlert(alarm) && alertEvaluator.resolveByDedupKey(dedupKey, alarm.rawBlock)) {
                    cleared++
                }
            } else if (shouldEmitAlert(alarm)) {
                activeDedupKeys += dedupKey
                val signal = signalExtractor.fromIngest(
                    targetId = targetId,
                    reasonCode = alarm.reasonCode,
                    severity = alarm.severity,
                    title = alertTitle(alarm),
                    component = component,
                    details = alarm.rawBlock
                )
                val eval = alertEvaluator.evaluateIngest(targetId, listOf(signal))
                alertsEmitted++
                opened += eval.openedIncidentIds
                eval.openedIncidentIds.firstOrNull()?.let { event.incidentId = it }
            }

            events += event
        }

        val saved = logEventRepository.saveAll(events)
        cleared += reconcileMissingActive(oltTargetId, activeDedupKeys)

        val unparsed = saved.count { it.isUnparsed }
        return OltAlarmIngestResult(
            persisted = saved.size,
            parsed = saved.size - unparsed,
            unparsed = unparsed,
            alertsEmitted = alertsEmitted,
            cleared = cleared,
            openedIncidentIds = opened,
            eventIds = saved.mapNotNull { it.id }
        )
    }

    private fun reconcileMissingActive(oltTargetId: Long?, activeDedupKeys: Set<String>): Int {
        if (oltTargetId == null) return 0
        val childIds = targetRepository.findByParentTargetId(oltTargetId).mapNotNull { it.id }
        val scopeIds = listOf(oltTargetId) + childIds
        if (scopeIds.isEmpty()) return 0
        val open = incidentRepository.findByTarget_IdInAndStatus(scopeIds, "OPEN")
        var cleared = 0
        open.forEach { incident ->
            if (incident.reasonCode !in ALERTABLE_REASON_CODES) return@forEach
            if (incident.dedupKey in activeDedupKeys) return@forEach
            if (alertEvaluator.resolveByDedupKey(incident.dedupKey, "absent from OLT active alarms")) {
                cleared++
            }
        }
        return cleared
    }

    private fun resolveTargetId(alarm: ParsedOltAlarm, oltTargetId: Long?): Long? {
        val slot = alarm.slotId
        val port = alarm.portId
        if (slot != null && port != null) {
            val name = OltNetDiagTargetSyncService.ponTargetName(oltId, slot, port)
            val pon = targetRepository.findByName(name).orElse(null)
            if (pon?.id != null) return pon.id
        }
        return oltTargetId
    }

    private fun shouldEmitAlert(alarm: ParsedOltAlarm): Boolean {
        return alarm.reasonCode in ALERTABLE_REASON_CODES
    }

    private fun alertComponent(alarm: ParsedOltAlarm): String {
        val base = alarm.component
        return if (alarm.ontId != null && alarm.reasonCode.startsWith("ONT_")) {
            "$base:ont-${alarm.ontId}"
        } else {
            base
        }
    }

    private fun buildDedupKey(reasonCode: String, targetId: Long?, component: String): String {
        val keyTarget = targetId?.toString() ?: "global"
        return "$reasonCode:$keyTarget:$component"
    }

    private fun alertTitle(alarm: ParsedOltAlarm): String {
        val where = alarm.component
        val ont = alarm.ontId?.let { " ont=$it" }.orEmpty()
        return "${alarm.reasonCode} $where$ont".trim()
    }

    private fun toEvent(
        alarm: ParsedOltAlarm,
        sourceIp: String?,
        targetId: Long?,
        channel: String,
        receivedAt: Instant
    ): NetDiagOltLogEvent {
        val unparsed = alarm.reasonCode == HuaweiOltAlarmParser.REASON_UNPARSED
        return NetDiagOltLogEvent(
            receivedAt = receivedAt,
            sourceIp = sourceIp,
            rawMessage = alarm.rawBlock,
            reasonCode = alarm.reasonCode,
            board = alarm.slotId,
            port = alarm.portId,
            onuIndex = alarm.ontId,
            targetId = targetId,
            severity = alarm.severity,
            channel = channel,
            alarmIdHex = alarm.alarmIdHex,
            alarmName = alarm.alarmName,
            component = alarm.component,
            isClear = alarm.isClear,
            isUnparsed = unparsed
        )
    }

    companion object {
        val ALERTABLE_REASON_CODES = setOf(
            "PON_PORT_DOWN",
            "PON_PORT_HW_FAULT",
            "PON_OPTICS_ABSENT",
            "PON_ROGUE_ONT",
            "PON_RANGING_FAIL",
            "PON_MASS_POWER_OFF",
            "PON_PROTECTION_FIBER",
            "ONT_OFFLINE",
            "ONT_DYING_GASP",
            "ONT_LOFI",
            "ONT_SFI",
            "ONT_SDI",
            "ONT_LCDGI",
            "ONT_RDI",
            "ONT_LOAMI",
            "ONT_DFI",
            "ONT_PEE",
            "ONT_INITIATIVE_OFFLINE",
            "ONT_AUTH_INVALID",
            "ONT_CONFIG_RECOVERY_FAIL",
            "ONT_DOWNSTREAM_SD",
            "ONT_DOWNSTREAM_SF",
            "ONT_OPTICAL_ALARM",
            "ONT_OPTICAL_WARNING",
            "ONT_HW_FAULT",
            "ONT_ETH_LOS",
            "ONT_BATTERY",
            "ONT_DOWI_THRESHOLD",
            "ONT_FEC_CORRECTABLE",
            "ONT_FEC_UNCORRECTABLE",
            "ONT_LOOCI_THRESHOLD",
            "OLT_BOARD_FAULT",
            "OLT_CONTROL_BOARD_FAULT",
            "OLT_POWER_FAULT",
            "OLT_FAN_FAULT",
            "OLT_TEMP_HIGH",
            "OLT_UPLINK_DOWN",
            "OLT_ALARM"
        )
    }
}
