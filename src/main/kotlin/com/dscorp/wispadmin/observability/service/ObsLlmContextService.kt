package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.dto.EventDto
import com.dscorp.wispadmin.observability.dto.IssueSummaryDto
import com.dscorp.wispadmin.observability.dto.NPlusOneCandidateDto
import com.dscorp.wispadmin.observability.dto.DbQueryAggregateDto
import com.dscorp.wispadmin.observability.dto.ReplaySummaryDto
import com.dscorp.wispadmin.observability.dto.SessionDetailDto
import com.dscorp.wispadmin.observability.dto.SpanDto
import com.dscorp.wispadmin.observability.dto.TraceDetailDto
import com.dscorp.wispadmin.observability.dto.TraceSummaryDto
import com.dscorp.wispadmin.observability.entity.ObsAlertEvent
import com.dscorp.wispadmin.observability.repository.ObsAlertEventRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class ObsLlmContextService(
    private val issueQueryService: ObsQueryService,
    private val traceQueryService: ObsTraceQueryService,
    private val sessionQueryService: ObsSessionQueryService,
    private val databaseQueryService: ObsDatabaseQueryService,
    private val alertEventRepository: ObsAlertEventRepository,
    private val objectMapper: ObjectMapper
) {

    private companion object {
        const val STACKTRACE_MAX = 6000
        const val SQL_MAX = 400
        const val MAX_BREADCRUMBS = 50
        const val MAX_SPANS = 120
        const val MAX_ALERTS = 15
        const val MAX_DB_QUERIES = 15
        const val MAX_SESSION_EVENTS = 60
        const val WINDOW_MINUTES = 5L
    }

    fun buildIssueContext(issueId: Long): String? {
        val issue = issueQueryService.getIssue(issueId) ?: return null
        val event = issueQueryService.getLatestEvent(issueId)
        val trace = event?.correlationId?.takeIf { it.isNotBlank() }?.let { traceQueryService.getTrace(it) }
        val session = event?.sessionId?.takeIf { it.isNotBlank() }?.let { sessionQueryService.getSession(it) }
        val alerts = findAlerts(issueId)

        val sb = StringBuilder()
        appendPreamble(sb, "issue")
        appendIssueSummary(sb, issue)
        appendEvent(sb, event)
        appendStacktrace(sb, event)
        appendBreadcrumbs(sb, event)
        appendJsonSections(sb, event)
        appendTrace(sb, trace)
        appendDatabaseWindow(sb, event?.eventTimestamp)
        appendSession(sb, session)
        appendAlerts(sb, alerts)
        appendReplay(sb, event, session)
        return sb.toString()
    }

    fun buildTraceContext(traceId: String): String? {
        val trace = traceQueryService.getTrace(traceId) ?: return null
        val event = trace.events.firstOrNull()
        val session = event?.sessionId?.takeIf { it.isNotBlank() }?.let { sessionQueryService.getSession(it) }
        val windowRef = event?.eventTimestamp ?: firstSpanTimestamp(trace)

        val sb = StringBuilder()
        appendPreamble(sb, "trace")
        appendTraceHeader(sb, trace)
        appendEvent(sb, event)
        appendStacktrace(sb, event)
        appendBreadcrumbs(sb, event)
        appendJsonSections(sb, event)
        appendTrace(sb, trace)
        appendDatabaseWindow(sb, windowRef)
        appendSession(sb, session)
        appendReplay(sb, event, session)
        return sb.toString()
    }

    fun buildSessionContext(sessionId: String): String? {
        val session = sessionQueryService.getSession(sessionId) ?: return null
        val errorEvent = session.events.firstOrNull { !it.stacktrace.isNullOrBlank() || !it.stacktraceSymbolicated.isNullOrBlank() }
            ?: session.events.firstOrNull()
        val trace = errorEvent?.correlationId?.takeIf { it.isNotBlank() }?.let { traceQueryService.getTrace(it) }

        val sb = StringBuilder()
        appendPreamble(sb, "session")
        appendSession(sb, session)
        appendEvent(sb, errorEvent)
        appendStacktrace(sb, errorEvent)
        appendBreadcrumbs(sb, errorEvent)
        appendJsonSections(sb, errorEvent)
        appendTrace(sb, trace)
        appendReplay(sb, errorEvent, session)
        return sb.toString()
    }

    private fun appendPreamble(sb: StringBuilder, scope: String) {
        val subject = when (scope) {
            "trace" -> "una traza distribuida"
            "session" -> "una sesión de usuario"
            else -> "un error (issue) de producción"
        }
        sb.append("# Contexto para diagnóstico con LLM\n\n")
        sb.append("Actúa como ingeniero senior de backend, Android y web. ")
        sb.append("A continuación se te entrega todo el contexto disponible sobre ").append(subject).append(". ")
        sb.append("Tu objetivo es diagnosticar la causa raíz y proponer un fix concreto.\n\n")
        sb.append("Formato de respuesta esperado:\n")
        sb.append("1. **Causa raíz probable** (con la evidencia del contexto que la sustenta).\n")
        sb.append("2. **Fix propuesto** (archivos/áreas a tocar y cambios concretos).\n")
        sb.append("3. **Verificación** (cómo confirmar que el problema queda resuelto).\n")
        sb.append("4. **Riesgos / dudas** si falta información.\n\n")
        sb.append("---\n\n")
    }

    private fun appendIssueSummary(sb: StringBuilder, issue: IssueSummaryDto) {
        sb.append("## Resumen del issue\n\n")
        appendField(sb, "Título", issue.title)
        appendField(sb, "Fingerprint", issue.fingerprint)
        appendField(sb, "Plataforma", issue.platform)
        appendField(sb, "Severidad", issue.severity)
        appendField(sb, "Estado", issue.status.name)
        appendField(sb, "Tipo de error", issue.errorType)
        appendField(sb, "Ocurrencias", issue.eventCount.toString())
        appendField(sb, "Primera vez", issue.firstSeen?.toString())
        appendField(sb, "Última vez", issue.lastSeen?.toString())
        appendField(sb, "Entorno", issue.lastEnvironment)
        appendField(sb, "Release", issue.lastRelease)
        appendField(sb, "Ticket", issue.trackerIssueKey ?: issue.jiraIssueKey)
        appendField(sb, "Ticket URL", issue.trackerBrowseUrl)
        sb.append("\n")
    }

    private fun appendEvent(sb: StringBuilder, event: EventDto?) {
        if (event == null) {
            sb.append("## Último evento\n\n_No hay evento disponible._\n\n")
            return
        }
        sb.append("## Último evento\n\n")
        appendField(sb, "Mensaje", event.message)
        appendField(sb, "Tipo de error", event.errorType)
        appendField(sb, "Severidad", event.severity)
        appendField(sb, "Plataforma", event.platform)
        appendField(sb, "Feature/Action", listOfNotNull(event.feature, event.action).joinToString(" / ").takeIf { it.isNotBlank() })
        appendField(
            sb,
            "Workflow",
            listOfNotNull(event.workflowName, event.workflowId?.let { "($it)" }, event.workflowStatus)
                .joinToString(" ")
                .takeIf { it.isNotBlank() }
        )
        appendField(sb, "Timestamp", (event.eventTimestamp ?: event.createdAt)?.toString())
        appendField(sb, "Trace-Id (correlationId)", event.correlationId)
        appendField(sb, "Session-Id", event.sessionId)
        appendField(sb, "URL", event.url)
        appendField(sb, "HTTP", listOfNotNull(event.httpMethod, event.httpStatus?.toString()).joinToString(" ").takeIf { it.isNotBlank() })
        appendField(sb, "Duración (ms)", event.durationMs?.toString())
        appendField(sb, "User-Agent", event.userAgent)
        appendField(sb, "Environment", event.environment)
        appendField(sb, "Release", event.release)
        sb.append("\n")
    }

    private fun appendStacktrace(sb: StringBuilder, event: EventDto?) {
        val stacktrace = event?.stacktraceSymbolicated?.takeIf { it.isNotBlank() } ?: event?.stacktrace?.takeIf { it.isNotBlank() }
        if (stacktrace.isNullOrBlank()) return
        val symbolicated = !event?.stacktraceSymbolicated.isNullOrBlank()
        sb.append("## Stacktrace")
        if (symbolicated) sb.append(" (simbolizado)")
        sb.append("\n\n```\n")
        sb.append(stacktrace.take(STACKTRACE_MAX))
        if (stacktrace.length > STACKTRACE_MAX) sb.append("\n... [truncado]")
        sb.append("\n```\n\n")
    }

    private fun appendBreadcrumbs(sb: StringBuilder, event: EventDto?) {
        val breadcrumbs = event?.breadcrumbs as? List<*> ?: return
        if (breadcrumbs.isEmpty()) return
        sb.append("## Breadcrumbs (cronológicos)\n\n")
        val limited = breadcrumbs.take(MAX_BREADCRUMBS)
        limited.forEachIndexed { index, crumb ->
            sb.append(index + 1).append(". ").append(compactJson(crumb)).append("\n")
        }
        if (breadcrumbs.size > MAX_BREADCRUMBS) {
            sb.append("... (").append(breadcrumbs.size - MAX_BREADCRUMBS).append(" más omitidos)\n")
        }
        sb.append("\n")
    }

    private fun appendJsonSections(sb: StringBuilder, event: EventDto?) {
        if (event == null) return
        appendJsonBlock(sb, "Tags", event.tags)
        appendJsonBlock(sb, "Context", event.context)
        appendJsonBlock(sb, "Device", event.device)
        appendJsonBlock(sb, "User", event.user)
    }

    private fun appendJsonBlock(sb: StringBuilder, title: String, value: Any?) {
        if (value == null) return
        val json = compactJson(value)
        if (json.isBlank() || json == "null" || json == "{}" || json == "[]") return
        sb.append("### ").append(title).append("\n\n```json\n").append(json).append("\n```\n\n")
    }

    private fun appendTraceHeader(sb: StringBuilder, trace: TraceDetailDto) {
        sb.append("## Traza\n\n")
        appendField(sb, "Trace-Id", trace.traceId)
        appendField(sb, "Spans", trace.spans.size.toString())
        appendField(sb, "Eventos correlacionados", trace.events.size.toString())
        sb.append("\n")
    }

    private fun appendTrace(sb: StringBuilder, trace: TraceDetailDto?) {
        if (trace == null || trace.spans.isEmpty()) return
        sb.append("## Traza distribuida (waterfall de spans)\n\n")
        sb.append("Formato: `indent nombre [kind] dur=Xms self=Yms status http SQL`. Los spans con error se marcan con [ERROR].\n\n")
        sb.append("```\n")
        renderWaterfall(sb, trace.spans)
        sb.append("```\n\n")
    }

    private fun renderWaterfall(sb: StringBuilder, spans: List<SpanDto>) {
        val byId = spans.filter { it.spanId != null }.associateBy { it.spanId }
        val childrenDuration = HashMap<String, Long>()
        spans.forEach { span ->
            val parentId = span.parentSpanId
            if (parentId != null && byId.containsKey(parentId)) {
                childrenDuration[parentId] = (childrenDuration[parentId] ?: 0L) + (span.durationMs ?: 0L)
            }
        }
        val childrenByParent = spans.groupBy { it.parentSpanId }
        val roots = spans.filter { it.parentSpanId == null || !byId.containsKey(it.parentSpanId) }
            .sortedBy { it.startEpochMs ?: 0L }

        var rendered = 0
        fun renderSpan(span: SpanDto, depth: Int) {
            if (rendered >= MAX_SPANS) return
            rendered++
            val indent = "  ".repeat(depth)
            val duration = span.durationMs ?: 0L
            val self = (duration - (childrenDuration[span.spanId] ?: 0L)).coerceAtLeast(0L)
            sb.append(indent).append(span.name ?: "(sin nombre)")
            span.kind?.let { sb.append(" [").append(it).append("]") }
            sb.append(" dur=").append(duration).append("ms self=").append(self).append("ms")
            span.status?.let { sb.append(" status=").append(it) }
            val http = listOfNotNull(span.httpMethod, span.httpRoute, span.httpStatus?.toString()).joinToString(" ")
            if (http.isNotBlank()) sb.append(" http=").append(http)
            span.dbStatement?.takeIf { it.isNotBlank() }?.let {
                sb.append(" SQL=").append(truncateSql(it))
            }
            if (span.status.equals("ERROR", ignoreCase = true) || (span.httpStatus ?: 0) >= 500) {
                sb.append(" [ERROR]")
            }
            sb.append("\n")
            childrenByParent[span.spanId]
                ?.sortedBy { it.startEpochMs ?: 0L }
                ?.forEach { renderSpan(it, depth + 1) }
        }
        roots.forEach { renderSpan(it, 0) }
        if (spans.size > rendered) {
            sb.append("... (").append(spans.size - rendered).append(" spans más omitidos)\n")
        }
    }

    private fun appendDatabaseWindow(sb: StringBuilder, reference: LocalDateTime?) {
        if (reference == null) return
        val fromMs = reference.minusMinutes(WINDOW_MINUTES).toEpochMs()
        val toMs = reference.plusMinutes(WINDOW_MINUTES).toEpochMs()
        val topQueries = safe { databaseQueryService.topQueries(fromMs, toMs, MAX_DB_QUERIES) } ?: emptyList()
        val nPlusOne = safe { databaseQueryService.nPlusOne(fromMs, toMs, 5) } ?: emptyList()
        if (topQueries.isEmpty() && nPlusOne.isEmpty()) return

        sb.append("## SQL / N+1 (ventana temporal ±").append(WINDOW_MINUTES).append(" min)\n\n")
        if (topQueries.isNotEmpty()) {
            sb.append("### Top queries\n\n")
            topQueries.forEach { appendDbQuery(sb, it) }
            sb.append("\n")
        }
        if (nPlusOne.isNotEmpty()) {
            sb.append("### Candidatos N+1\n\n")
            nPlusOne.forEach { appendNPlusOne(sb, it) }
            sb.append("\n")
        }
    }

    private fun appendDbQuery(sb: StringBuilder, query: DbQueryAggregateDto) {
        sb.append("- calls=").append(query.calls)
            .append(" total=").append(query.totalMs).append("ms")
            .append(" avg=").append(String.format("%.1f", query.avgMs)).append("ms")
            .append(" max=").append(query.maxMs).append("ms")
            .append(" pct=").append(String.format("%.1f", query.pct * 100)).append("%")
            .append(" | ").append(truncateSql(query.statement)).append("\n")
    }

    private fun appendNPlusOne(sb: StringBuilder, candidate: NPlusOneCandidateDto) {
        sb.append("- repeticiones=").append(candidate.maxRepetitions)
            .append(" trazasAfectadas=").append(candidate.affectedTraces)
            .append(" total=").append(candidate.totalMs).append("ms")
        candidate.exampleTraceId?.let { sb.append(" traceEjemplo=").append(it) }
        sb.append(" | ").append(truncateSql(candidate.statement)).append("\n")
    }

    private fun appendSession(sb: StringBuilder, session: SessionDetailDto?) {
        if (session == null) return
        sb.append("## Sesión\n\n")
        appendField(sb, "Session-Id", session.summary.sessionId)
        appendField(sb, "Plataforma", session.summary.platform)
        appendField(sb, "Eventos", session.summary.eventCount.toString())
        appendField(sb, "Trazas", session.summary.traceCount.toString())
        appendField(sb, "Primera vez", session.summary.firstSeen?.toString())
        appendField(sb, "Última vez", session.summary.lastSeen?.toString())
        sb.append("\n")

        if (session.events.isNotEmpty()) {
            sb.append("### Timeline de eventos\n\n")
            session.events.take(MAX_SESSION_EVENTS).forEach { evt ->
                val ts = (evt.eventTimestamp ?: evt.createdAt)?.toString() ?: "-"
                val label = listOfNotNull(evt.feature, evt.action).joinToString("/").ifBlank { evt.eventType ?: "evento" }
                sb.append("- ").append(ts).append(" | ").append(label)
                evt.message?.takeIf { it.isNotBlank() }?.let { sb.append(" | ").append(it.take(200)) }
                sb.append("\n")
            }
            if (session.events.size > MAX_SESSION_EVENTS) {
                sb.append("... (").append(session.events.size - MAX_SESSION_EVENTS).append(" más omitidos)\n")
            }
            sb.append("\n")
        }

        if (session.traces.isNotEmpty()) {
            sb.append("### Trazas de la sesión\n\n")
            session.traces.forEach { appendSessionTrace(sb, it) }
            sb.append("\n")
        }
    }

    private fun appendSessionTrace(sb: StringBuilder, trace: TraceSummaryDto) {
        sb.append("- ").append(trace.rootName ?: "(root)")
            .append(" traceId=").append(trace.traceId ?: "-")
            .append(" dur=").append(trace.durationMs ?: 0L).append("ms")
            .append(" spans=").append(trace.spanCount)
        if (trace.hasError) sb.append(" [ERROR]")
        sb.append("\n")
    }

    private fun appendAlerts(sb: StringBuilder, alerts: List<ObsAlertEvent>) {
        if (alerts.isEmpty()) return
        sb.append("## Alertas relacionadas\n\n")
        alerts.forEach { alert ->
            val ts = alert.createdAt?.toString() ?: "-"
            sb.append("- ").append(ts)
                .append(" | ").append(alert.type.name)
                .append(" | ").append(alert.title ?: alert.ruleName ?: "-")
            if (alert.observedValue != null && alert.thresholdValue != null) {
                sb.append(" (observado=").append(alert.observedValue)
                    .append(", umbral=").append(alert.thresholdValue).append(")")
            }
            sb.append("\n")
        }
        sb.append("\n")
    }

    private fun appendReplay(sb: StringBuilder, event: EventDto?, session: SessionDetailDto?) {
        val replays = mutableListOf<String>()
        event?.replayId?.let { replays.add("Replay del evento: id=$it formato=${event.format ?: "-"}") }
        session?.replays?.forEach { replays.add(formatReplay(it)) }
        if (replays.isEmpty()) return
        sb.append("## Replay (solo metadata)\n\n")
        replays.distinct().forEach { sb.append("- ").append(it).append("\n") }
        sb.append("\n")
    }

    private fun formatReplay(replay: ReplaySummaryDto): String {
        return "Replay id=${replay.id} formato=${replay.format ?: "-"} workflow=${replay.workflowId ?: "-"} dur=${replay.durationMs ?: 0L}ms size=${replay.sizeBytes ?: 0L}bytes"
    }

    private fun findAlerts(issueId: Long): List<ObsAlertEvent> =
        safe { alertEventRepository.findByIssueIdOrderByCreatedAtDesc(issueId, PageRequest.of(0, MAX_ALERTS)) } ?: emptyList()

    private fun firstSpanTimestamp(trace: TraceDetailDto): LocalDateTime? {
        val startMs = trace.spans.mapNotNull { it.startEpochMs }.minOrNull() ?: return null
        return java.time.Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault()).toLocalDateTime()
    }

    private fun appendField(sb: StringBuilder, label: String, value: String?) {
        if (value.isNullOrBlank()) return
        sb.append("- **").append(label).append("**: ").append(value).append("\n")
    }

    private fun truncateSql(statement: String?): String {
        if (statement.isNullOrBlank()) return "-"
        val normalized = statement.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        return if (normalized.length > SQL_MAX) normalized.take(SQL_MAX) + " ... [truncado]" else normalized
    }

    private fun compactJson(value: Any?): String {
        if (value == null) return "null"
        return try {
            objectMapper.writeValueAsString(value)
        } catch (e: Exception) {
            value.toString()
        }
    }

    private fun LocalDateTime.toEpochMs(): Long =
        this.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun <T> safe(block: () -> T): T? = try {
        block()
    } catch (e: Exception) {
        null
    }
}
