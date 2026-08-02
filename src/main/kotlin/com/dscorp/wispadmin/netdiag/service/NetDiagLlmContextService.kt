package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentEventRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagProbeRunRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagTrapEventRepository
import com.dscorp.wispadmin.netdiag.exception.IncidentNotFoundException
import com.dscorp.wispadmin.netdiag.port.NetDiagRadiusImpactPort
import com.dscorp.wispadmin.netdiag.port.OntSubscriptionInfo
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
    private val radiusImpactPort: NetDiagRadiusImpactPort,
    private val gponContextBuilder: NetDiagLlmGponContextBuilder,
    private val objectMapper: ObjectMapper
) {

    fun buildMarkdown(incidentId: Long): String {
        val incident = incidentRepository.findByIdWithTarget(incidentId)
            .orElseThrow { IncidentNotFoundException("Incident not found: $incidentId") }
        val events = incidentEventRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId)
        val targetId = incident.target?.id
        val gponContext = gponContextBuilder.build(incident)

        val sb = StringBuilder()
        appendLlmHeader(sb)
        appendIncidentSection(sb, incident, targetId)
        appendTimelineSection(sb, events)

        if (gponContext != null) {
            appendGponMarkdown(sb, gponContext)
            return sb.toString()
        }

        val latestProbe = targetId?.let {
            probeRunRepository.findTopByTargetIdOrderByStartedAtDesc(it).orElse(null)
        }
        val probeNode = latestProbe?.payload?.let { parseJson(it) }
        val recentTraps = targetId?.let {
            trapEventRepository.findTop20ByTargetIdOrderByReceivedAtDesc(it)
        }.orEmpty()

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

        val radiusImpact = radiusImpactPort.estimateImpact(targetId, incident.target?.deviceRefId)
        sb.append("## Impacto suscriptores (RADIUS/PPP)\n\n")
        appendField(sb, "Fuente", radiusImpact.source)
        appendField(sb, "Suscripciones activas", radiusImpact.activeSubscriptions.toString())
        appendField(sb, "Sesiones PPP activas", radiusImpact.pppActiveSessions?.toString())
        appendField(sb, "Estimado afectados", radiusImpact.estimatedAffected?.toString())
        appendField(sb, "Notas", radiusImpact.notes)
        sb.append("\n")

        return sb.toString()
    }

    fun buildDiagnosticJson(incidentId: Long): Map<String, Any?> {
        val incident = incidentRepository.findByIdWithTarget(incidentId)
            .orElseThrow { IncidentNotFoundException("Incident not found: $incidentId") }
        val events = incidentEventRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId)
        val targetId = incident.target?.id
        val gponContext = gponContextBuilder.build(incident)

        val base = linkedMapOf<String, Any?>(
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
            }
        )

        if (gponContext != null) {
            base["targetContext"] = gponTargetContextMap(gponContext.targetContext)
            base["recentOltLogs"] = gponContext.recentOltLogs.map { gponLogMap(it) }
            base["ponInventory"] = gponContext.ponInventory?.let { ponInventoryMap(it) }
            base["ontSubscription"] = gponContext.ontSubscription?.let { ontSubscriptionMap(it) }
            return base
        }

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
        val radiusImpact = radiusImpactPort.estimateImpact(targetId, incident.target?.deviceRefId)

        base["healthSnapshot"] = jsonValue(probeNode?.get("health"))
        base["netwatchSnapshot"] = jsonValue(probeNode?.get("netwatch"))
        base["opticalSnapshot"] = jsonValue(probeNode?.get("optical"))
        base["radiusImpact"] = linkedMapOf(
            "source" to radiusImpact.source,
            "activeSubscriptions" to radiusImpact.activeSubscriptions,
            "pppActiveSessions" to radiusImpact.pppActiveSessions,
            "estimatedAffected" to radiusImpact.estimatedAffected,
            "notes" to radiusImpact.notes
        )
        base["recentTraps"] = recentTraps.map { trap ->
            linkedMapOf(
                "id" to trap.id,
                "reasonCode" to trap.reasonCode,
                "trapType" to trap.trapType,
                "component" to trap.component,
                "sourceHost" to trap.sourceHost,
                "receivedAt" to trap.receivedAt.toString()
            )
        }
        base["latestProbe"] = latestProbe?.let { probe ->
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
        return base
    }

    private fun appendLlmHeader(sb: StringBuilder) {
        sb.append("# Contexto para diagnóstico NOC con LLM\n\n")
        sb.append("Actúa como ingeniero senior de redes (MikroTik RouterOS / GPON). ")
        sb.append("Tu objetivo es diagnosticar la causa raíz del incidente y proponer acciones concretas.\n\n")
        sb.append("Formato de respuesta esperado:\n")
        sb.append("1. **Causa raíz probable** (con evidencia del contexto).\n")
        sb.append("2. **Acciones inmediatas** (comandos/checks).\n")
        sb.append("3. **Verificación** (cómo confirmar resolución).\n")
        sb.append("4. **Riesgos / dudas** si falta información.\n\n")
        sb.append("---\n\n")
    }

    private fun appendIncidentSection(sb: StringBuilder, incident: com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncident, targetId: Long?) {
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
    }

    private fun appendTimelineSection(sb: StringBuilder, events: List<com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncidentEvent>) {
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
    }

    private fun appendGponMarkdown(sb: StringBuilder, gpon: GponLlmContext) {
        val ctx = gpon.targetContext
        sb.append("## Target GPON\n\n")
        appendField(sb, "Kind", ctx.kind)
        appendField(sb, "OLT ID", ctx.oltId)
        appendField(sb, "Board", ctx.board?.toString())
        appendField(sb, "Port", ctx.port?.toString())
        appendField(sb, "Mgmt IP", ctx.mgmtIp)
        appendField(sb, "Target padre", ctx.parentTargetName)
        sb.append("\n")

        sb.append("## Alarmas OLT recientes\n\n")
        if (gpon.recentOltLogs.isEmpty()) {
            sb.append("_Sin alarmas recientes._\n\n")
        } else {
            gpon.recentOltLogs.forEach { log ->
                sb.append("- `").append(log.receivedAt).append("` ")
                appendInlineField(sb, "reason", log.reasonCode)
                appendInlineField(sb, "ont", log.onuIndex?.toString())
                appendInlineField(sb, "severity", log.severity)
                appendInlineField(sb, "clear", if (log.isClear) "yes" else "no")
                if (!log.alarmName.isNullOrBlank()) {
                    sb.append(" **").append(log.alarmName).append("**")
                }
                sb.append("\n")
                if (log.rawMessage.isNotBlank()) {
                    sb.append("  ").append(log.rawMessage.replace("\n", " ")).append("\n")
                }
            }
            sb.append("\n")
        }

        gpon.ponInventory?.let { inventory ->
            sb.append("## Inventario PON\n\n")
            appendField(sb, "Total ONUs", inventory.total.toString())
            appendField(sb, "Online", inventory.online.toString())
            appendField(sb, "Offline", inventory.offline.toString())
            if (inventory.offlineSample.isNotEmpty()) {
                sb.append("\nMuestra offline:\n\n")
                inventory.offlineSample.forEach { onu ->
                    sb.append("- ont=").append(onu.onuIndex)
                        .append(" sn=").append(onu.sn)
                        .append(" state=").append(onu.runState ?: "-")
                    if (!onu.lastDownCause.isNullOrBlank()) {
                        sb.append(" lastDown=").append(onu.lastDownCause)
                    }
                    onu.onuRxDbm?.let { sb.append(" rxDbm=").append(it) }
                    sb.append("\n")
                }
            }
            sb.append("\n")
        }

        gpon.ontSubscription?.let { ont ->
            sb.append("## Abonado ONT\n\n")
            appendField(sb, "ONT index", ont.onuIndex.toString())
            appendField(sb, "SN", ont.sn)
            appendField(sb, "Run state", ont.runState)
            appendField(sb, "Last down cause", ont.lastDownCause)
            ont.subscription?.let { sub ->
                appendField(sb, "Suscripción ID", sub.subscriptionId?.toString())
                appendField(sb, "Cliente", sub.customerName)
                appendField(sb, "Estado servicio", sub.serviceStatus)
                appendField(sb, "NAP", sub.napBoxCode)
            }
            sb.append("\n")
        }
    }

    private fun appendInlineField(sb: StringBuilder, label: String, value: String?) {
        if (value.isNullOrBlank()) return
        sb.append(label).append("=").append(value).append(" ")
    }

    private fun gponTargetContextMap(ctx: GponTargetContext): Map<String, Any?> = linkedMapOf(
        "kind" to ctx.kind,
        "oltId" to ctx.oltId,
        "board" to ctx.board,
        "port" to ctx.port,
        "mgmtIp" to ctx.mgmtIp,
        "parentTargetName" to ctx.parentTargetName
    )

    private fun gponLogMap(log: GponOltLogEntry): Map<String, Any?> = linkedMapOf(
        "receivedAt" to log.receivedAt.toString(),
        "reasonCode" to log.reasonCode,
        "onuIndex" to log.onuIndex,
        "severity" to log.severity,
        "alarmName" to log.alarmName,
        "isClear" to log.isClear,
        "rawMessage" to log.rawMessage
    )

    private fun ponInventoryMap(inventory: PonInventory): Map<String, Any?> = linkedMapOf(
        "total" to inventory.total,
        "online" to inventory.online,
        "offline" to inventory.offline,
        "offlineSample" to inventory.offlineSample.map { onu ->
            linkedMapOf(
                "onuIndex" to onu.onuIndex,
                "sn" to onu.sn,
                "runState" to onu.runState,
                "lastDownCause" to onu.lastDownCause,
                "onuRxDbm" to onu.onuRxDbm
            )
        }
    )

    private fun ontSubscriptionMap(ont: OntSubscriptionContext): Map<String, Any?> = linkedMapOf(
        "onuIndex" to ont.onuIndex,
        "sn" to ont.sn,
        "runState" to ont.runState,
        "lastDownCause" to ont.lastDownCause,
        "subscription" to ont.subscription?.let { subscriptionMap(it) }
    )

    private fun subscriptionMap(sub: OntSubscriptionInfo): Map<String, Any?> = linkedMapOf(
        "subscriptionId" to sub.subscriptionId,
        "customerName" to sub.customerName,
        "serviceStatus" to sub.serviceStatus,
        "napBoxCode" to sub.napBoxCode
    )

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
