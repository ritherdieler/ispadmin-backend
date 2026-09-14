package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.config.NetDiagProperties
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagTargetRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Service
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
class SyslogIngestAdapter(
    private val targetRepository: NetDiagTargetRepository,
    private val signalExtractor: AlertSignalExtractor,
    private val alertEvaluator: AlertEvaluator,
    private val properties: NetDiagProperties
) {

    private val pppEvents = ConcurrentHashMap<Long, MutableList<Long>>()

    fun ingest(targetId: Long?, message: String): AlertEvaluationResult {
        if (targetId != null && !targetRepository.findById(targetId).isPresent) {
            return AlertEvaluationResult(decisions = emptyList(), openedIncidentIds = emptyList())
        }
        val classification = classify(message) ?: return AlertEvaluationResult(
            decisions = emptyList(),
            openedIncidentIds = emptyList()
        )
        if (classification.reasonCode == "PPP_DISCONNECT") {
            return handlePppDisconnect(targetId, message)
        }
        val signal = signalExtractor.fromIngest(
            targetId = targetId,
            reasonCode = classification.reasonCode,
            severity = classification.severity,
            title = classification.title,
            component = classification.component,
            details = classification.details ?: message
        )
        return alertEvaluator.evaluateIngest(targetId, listOf(signal))
    }

    fun classify(message: String): SyslogClassification? {
        val lower = message.lowercase()
        return when {
            lower.contains("loop-protect") || lower.contains("loop protect") ->
                SyslogClassification(
                    reasonCode = "LOOP_PROTECT_TRIGGERED",
                    severity = "P0",
                    title = "Loop protect triggered",
                    component = extractBridge(message) ?: "bridge",
                    details = message
                )
            lower.contains("link down") || (lower.contains("link") && lower.contains("down")) ->
                SyslogClassification(
                    reasonCode = "LINK_FLAP",
                    severity = "P1",
                    title = "Link flap / link down (syslog)",
                    component = extractIface(message) ?: "link",
                    details = message
                )
            lower.contains("temperature") && (lower.contains("critical") || lower.contains("high") || lower.contains("exception")) ->
                SyslogClassification(
                    reasonCode = "HIGH_TEMPERATURE",
                    severity = "P0",
                    title = "High temperature (syslog)",
                    component = "temperature",
                    details = message
                )
            lower.contains("pppoe") && (lower.contains("disconnect") || lower.contains("logged out") || lower.contains("terminated")) ->
                SyslogClassification(
                    reasonCode = "PPP_DISCONNECT",
                    severity = "P1",
                    title = "PPP disconnect",
                    component = "ppp",
                    details = message
                )
            lower.contains("ppp") && lower.contains("disconnect") ->
                SyslogClassification(
                    reasonCode = "PPP_DISCONNECT",
                    severity = "P1",
                    title = "PPP disconnect",
                    component = "ppp",
                    details = message
                )
            else -> null
        }
    }

    private fun handlePppDisconnect(targetId: Long?, message: String): AlertEvaluationResult {
        if (targetId == null) {
            return AlertEvaluationResult(decisions = emptyList(), openedIncidentIds = emptyList())
        }
        val now = Instant.now().toEpochMilli()
        val windowMs = properties.syslog.pppMassWindowSeconds.coerceAtLeast(1) * 1000L
        val threshold = properties.syslog.pppMassThreshold.coerceAtLeast(1)
        val bucket = pppEvents.computeIfAbsent(targetId) { mutableListOf() }
        synchronized(bucket) {
            bucket.removeIf { now - it > windowMs }
            bucket += now
            if (bucket.size < threshold) {
                return AlertEvaluationResult(decisions = emptyList(), openedIncidentIds = emptyList())
            }
            val signal = signalExtractor.fromIngest(
                targetId = targetId,
                reasonCode = "PPP_MASS_DISCONNECT",
                severity = "P0",
                title = "PPP mass disconnect",
                component = "ppp",
                details = "count=${bucket.size};windowSeconds=${properties.syslog.pppMassWindowSeconds};sample=$message"
            )
            bucket.clear()
            return alertEvaluator.evaluateIngest(targetId, listOf(signal))
        }
    }

    private fun extractBridge(message: String): String? {
        Regex("""\bbridge\d+\b""", RegexOption.IGNORE_CASE).find(message)?.value?.let { return it }
        Regex("""\bon\s+(bridge[^\s,:]*)""", RegexOption.IGNORE_CASE).find(message)?.groupValues?.get(1)
            ?.let { return it }
        return "bridge"
    }

    private fun extractIface(message: String): String? {
        return Regex("""(?:interface\s+)?(ether\d+|sfp[-\w]+|bridge\d+)""", RegexOption.IGNORE_CASE)
            .find(message)?.groupValues?.get(1)
    }
}
