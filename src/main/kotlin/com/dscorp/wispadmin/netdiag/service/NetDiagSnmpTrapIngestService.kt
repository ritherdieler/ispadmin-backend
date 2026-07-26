package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTrapEvent
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagTrapEventRepository
import com.dscorp.wispadmin.netdiag.dto.TrapIngestRequestDto
import com.dscorp.wispadmin.netdiag.dto.TrapIngestResponseDto
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.time.Instant

@Service
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
class NetDiagSnmpTrapIngestService(
    private val trapRepository: NetDiagTrapEventRepository,
    private val signalExtractor: AlertSignalExtractor,
    private val alertEvaluator: AlertEvaluator
) {

    fun ingest(request: TrapIngestRequestDto): TrapIngestResponseDto {
        val reasonCode = mapReasonCode(request.trapType, request.specificType)
        val component = request.component?.ifBlank { null }
            ?: extractComponent(request.varBinds)
            ?: request.trapType?.ifBlank { null }
            ?: "trap"
        val title = "SNMP trap ${humanLabel(reasonCode)}: $component"
        val severity = when (reasonCode) {
            "SNMP_TRAP_GENERIC" -> "P1"
            else -> "P0"
        }
        val event = trapRepository.save(
            NetDiagTrapEvent(
                targetId = request.targetId,
                sourceHost = request.sourceHost,
                trapType = request.trapType,
                oid = request.oid,
                reasonCode = reasonCode,
                component = component,
                varBinds = request.varBinds,
                raw = request.raw,
                receivedAt = Instant.now()
            )
        )
        val signal = signalExtractor.fromIngest(
            targetId = request.targetId,
            reasonCode = reasonCode,
            severity = severity,
            title = title,
            component = component,
            details = request.varBinds ?: request.raw
        )
        val result = alertEvaluator.evaluateIngest(request.targetId, listOf(signal))
        return TrapIngestResponseDto(
            decisions = result.decisions,
            openedIncidentIds = result.openedIncidentIds,
            suppressed = result.suppressed,
            trapEventId = event.id,
            reasonCode = reasonCode
        )
    }

    fun ingestUdp(payload: String, resolvedTargetId: Long?): TrapIngestResponseDto {
        val parsed = parseUdpPayload(payload)
        val request = TrapIngestRequestDto().apply {
            targetId = resolvedTargetId
            trapType = parsed.trapType
            sourceHost = parsed.sourceHost
            oid = parsed.oid
            raw = parsed.raw
            varBinds = parsed.raw
            component = extractComponent(parsed.raw)
        }
        return ingest(request)
    }

    fun mapReasonCode(trapType: String?, specificType: String?): String {
        val type = (trapType ?: "").lowercase()
        val specific = (specificType ?: "").lowercase()
        return when {
            type.contains("start") || type == "start-trap" -> "SNMP_TRAP_REBOOT"
            type.contains("temp") || type == "temp-exception" -> "SNMP_TRAP_TEMP"
            type.contains("interface") || specific.contains("linkdown") || specific.contains("link") ->
                "SNMP_TRAP_LINK_DOWN"
            type.isBlank() && specific.isBlank() -> "SNMP_TRAP_GENERIC"
            else -> "SNMP_TRAP_GENERIC"
        }
    }

    fun parseUdpPayload(payload: String): ParsedSnmpTrap {
        val host = Regex("""host=([^\s]+)""", RegexOption.IGNORE_CASE).find(payload)?.groupValues?.get(1)
        val type = Regex("""type=([^\s]+)""", RegexOption.IGNORE_CASE).find(payload)?.groupValues?.get(1)
        val oid = Regex("""oid=([^\s]+)""", RegexOption.IGNORE_CASE).find(payload)?.groupValues?.get(1)
        return ParsedSnmpTrap(
            sourceHost = host,
            trapType = type,
            oid = oid,
            raw = payload
        )
    }

    private fun extractComponent(varBinds: String?): String? {
        if (varBinds.isNullOrBlank()) return null
        Regex("""ifName=([^\s",}]+)""", RegexOption.IGNORE_CASE).find(varBinds)?.groupValues?.get(1)
            ?.let { return it }
        Regex(""""ifName"\s*:\s*"([^"]+)"""").find(varBinds)?.groupValues?.get(1)
            ?.let { return it }
        return null
    }

    private fun humanLabel(reasonCode: String): String {
        return when (reasonCode) {
            "SNMP_TRAP_LINK_DOWN" -> "link down"
            "SNMP_TRAP_REBOOT" -> "reboot"
            "SNMP_TRAP_TEMP" -> "temperature"
            else -> "event"
        }
    }
}
