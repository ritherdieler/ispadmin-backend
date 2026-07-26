package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentEventRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagProbeRunRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagTrapEventRepository
import com.dscorp.wispadmin.netdiag.exception.IncidentNotFoundException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
class NetDiagLlmContextService(
    private val incidentRepository: NetDiagIncidentRepository,
    private val incidentEventRepository: NetDiagIncidentEventRepository,
    private val probeRunRepository: NetDiagProbeRunRepository,
    private val trapEventRepository: NetDiagTrapEventRepository,
    private val objectMapper: ObjectMapper
) {

    fun buildMarkdown(incidentId: Long): String {
        val incident = incidentRepository.findById(incidentId)
            .orElseThrow { IncidentNotFoundException("Incident not found: $incidentId") }
        val events = incidentEventRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId)
        val targetId = incident.target?.id
        val latestProbe = targetId?.let {
            probeRunRepository.findTopByTargetIdOrderByStartedAtDesc(it).orElse(null)
        }
        val probeNode = latestProbe?.payload?.let { parseJson(it) }
        val recentTraps = targetId?.let {
            trapEventRepository.findTop20ByTargetIdOrderByReceivedAtDesc(it)
        }.orEmpty()

        val sb = StringBuilder()
        sb.append("# Contexto para diagnóstico NOC con LLM\n\n")
        sb.append("Actúa como ingeniero senior de redes (MikroTik RouterOS / GPON). ")
        sb.append("Tu objetivo es diagnosticar la causa raíz del incidente y proponer acciones concretas.\n\n")
        sb.append("Formato de respuesta esperado:\n")
        sb.append("1. **Causa raíz probable** (con evidencia del contexto).\n")
        sb.append("2. **Acciones inmediatas** (comandos/checks).\n")
        sb.append("3. **Verificación** (cómo confirmar resolución).\n")
        sb.append("4. **Riesgos / dudas** si falta información.\n\n")
        sb.append("---\n\n")

        sb.append("## Incidente\n\n")
        appendField(sb, "ID", incident.id?.toString())
        appendField(sb, "Título", incident.title)
        appendField(sb, "Severidad", incident.severity)
        appendField(sb, "Estado", incident.status)
        appendField(sb, "Reason code", incident.reasonCode)
        appendField(sb, "Dedup key", incident.dedupKey)
        appendField(sb, "Target", incident.target?.name)
        appendField(sb, "Target ID", targetId?.toString())
        appendField(sb, "Abierto", incident.openedAt.toString())
        appendField(sb, "Ack", incident.acknowledgedAt?.toString())
        appendField(sb, "Resuelto", incident.resolvedAt?.toString())
        appendField(sb, "Última notificación", incident.lastNotifiedAt?.toString())
        sb.append("\n")

        sb.append("## Timeline\n\n")
        if (events.isEmpty()) {
            sb.append("_Sin eventos._\n\n")
        } else {
            events.forEach { event ->
                sb.append("- `").append(event.createdAt).append("` **")
                    .append(event.type).append("**")
                if (!event.payload.isNullOrBlank()) {
                    sb.append(": ").append(event.payload)
                }
                sb.append("\n")
            }
            sb.append("\n")
        }

        sb.append("## Health snapshot\n\n")
        appendJsonSection(sb, probeNode?.get("health"))

        sb.append("## Netwatch snapshot\n\n")
        appendJsonSection(sb, probeNode?.get("netwatch"))

        sb.append("## Optical snapshot\n\n")
        appendJsonSection(sb, probeNode?.get("optical"))

        sb.append("## Último probe_run\n\n")
        if (latestProbe == null) {
            sb.append("_No hay probe_run para el target._\n\n")
        } else {
            appendField(sb, "Probe ID", latestProbe.id?.toString())
            appendField(sb, "Status", latestProbe.status)
            appendField(sb, "Started", latestProbe.startedAt.toString())
            appendField(sb, "Finished", latestProbe.finishedAt?.toString())
            appendField(sb, "Latency ms", latestProbe.latencyMs?.toString())
            appendField(sb, "Error", latestProbe.error)
            sb.append("\n```json\n")
            sb.append(latestProbe.payload ?: "{}")
            sb.append("\n```\n\n")
        }

        sb.append("## SNMP traps recientes\n\n")
        if (recentTraps.isEmpty()) {
            sb.append("_Sin traps recientes._\n\n")
        } else {
            recentTraps.forEach { trap ->
                sb.append("- `").append(trap.receivedAt).append("` ")
                    .append(trap.reasonCode).append(" type=")
                    .append(trap.trapType ?: "-")
                    .append(" component=")
                    .append(trap.component ?: "-")
                    .append("\n")
            }
            sb.append("\n")
        }

        return sb.toString()
    }

    fun buildDiagnosticJson(incidentId: Long): Map<String, Any?> {
        val incident = incidentRepository.findById(incidentId)
            .orElseThrow { IncidentNotFoundException("Incident not found: $incidentId") }
        val events = incidentEventRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId)
        val targetId = incident.target?.id
        val latestProbe = targetId?.let {
            probeRunRepository.findTopByTargetIdOrderByStartedAtDesc(it).orElse(null)
        }
        val probePayload: Any? = latestProbe?.payload?.let { raw ->
            runCatching { objectMapper.readTree(raw) }.getOrDefault(raw)
        }
        val probeNode = latestProbe?.payload?.let { parseJson(it) }
        val recentTraps = targetId?.let {
            trapEventRepository.findTop20ByTargetIdOrderByReceivedAtDesc(it)
        }.orEmpty()

        return linkedMapOf(
            "incidentId" to incident.id,
            "title" to incident.title,
            "severity" to incident.severity,
            "status" to incident.status,
            "reasonCode" to incident.reasonCode,
            "dedupKey" to incident.dedupKey,
            "targetId" to targetId,
            "targetName" to incident.target?.name,
            "openedAt" to incident.openedAt.toString(),
            "acknowledgedAt" to incident.acknowledgedAt?.toString(),
            "resolvedAt" to incident.resolvedAt?.toString(),
            "lastNotifiedAt" to incident.lastNotifiedAt?.toString(),
            "timeline" to events.map { event ->
                linkedMapOf(
                    "id" to event.id,
                    "type" to event.type,
                    "payload" to event.payload,
                    "createdAt" to event.createdAt.toString()
                )
            },
            "healthSnapshot" to jsonValue(probeNode?.get("health")),
            "netwatchSnapshot" to jsonValue(probeNode?.get("netwatch")),
            "opticalSnapshot" to jsonValue(probeNode?.get("optical")),
            "recentTraps" to recentTraps.map { trap ->
                linkedMapOf(
                    "id" to trap.id,
                    "reasonCode" to trap.reasonCode,
                    "trapType" to trap.trapType,
                    "component" to trap.component,
                    "sourceHost" to trap.sourceHost,
                    "receivedAt" to trap.receivedAt.toString()
                )
            },
            "latestProbe" to latestProbe?.let { probe ->
                linkedMapOf(
                    "id" to probe.id,
                    "status" to probe.status,
                    "startedAt" to probe.startedAt.toString(),
                    "finishedAt" to probe.finishedAt?.toString(),
                    "latencyMs" to probe.latencyMs,
                    "error" to probe.error,
                    "payload" to probePayload
                )
            }
        )
    }

    private fun parseJson(raw: String): JsonNode? {
        return runCatching { objectMapper.readTree(raw) }.getOrNull()
    }

    private fun jsonValue(node: JsonNode?): Any? {
        if (node == null || node.isMissingNode || node.isNull) return null
        return objectMapper.convertValue(node, Any::class.java)
    }

    private fun appendJsonSection(sb: StringBuilder, node: JsonNode?) {
        if (node == null || node.isMissingNode || node.isNull) {
            sb.append("_Sin datos._\n\n")
            return
        }
        sb.append("```json\n")
        sb.append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node))
        sb.append("\n```\n\n")
    }

    private fun appendField(sb: StringBuilder, label: String, value: String?) {
        if (value.isNullOrBlank()) return
        sb.append("- **").append(label).append("**: ").append(value).append("\n")
    }
}
