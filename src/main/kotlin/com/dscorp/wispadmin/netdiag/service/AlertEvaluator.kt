package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.config.NetDiagProperties
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagAlertSuppressionWindow
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncident
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncidentEvent
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagAlertSuppressionWindowRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentEventRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagTargetRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Service
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
class AlertEvaluator(
    private val incidentRepository: NetDiagIncidentRepository,
    private val incidentEventRepository: NetDiagIncidentEventRepository,
    private val suppressionWindowRepository: NetDiagAlertSuppressionWindowRepository,
    private val targetRepository: NetDiagTargetRepository,
    private val correlationEngine: CorrelationEngine,
    private val notifier: WhatsAppOpsNotifier,
    private val llmWebhookService: NetDiagLlmWebhookService,
    private val summaryCache: NetDiagIncidentSummaryCache,
    private val properties: NetDiagProperties
) {

    private val locks = ConcurrentHashMap<Long, Any>()

    fun evaluate(targetId: Long, signals: List<AlertSignal>): AlertEvaluationResult {
        if (signals.isEmpty()) {
            return AlertEvaluationResult(decisions = emptyList(), openedIncidentIds = emptyList())
        }
        val lock = locks.computeIfAbsent(targetId) { Any() }
        synchronized(lock) {
            return evaluateLocked(targetId, signals)
        }
    }

    fun evaluateIngest(targetId: Long?, signals: List<AlertSignal>): AlertEvaluationResult {
        val lockKey = targetId ?: -1L
        val lock = locks.computeIfAbsent(lockKey) { Any() }
        synchronized(lock) {
            return evaluateLocked(targetId, signals)
        }
    }

    fun reconcilePollSignals(targetId: Long, activeDedupKeys: Set<String>) {
        val lock = locks.computeIfAbsent(targetId) { Any() }
        synchronized(lock) {
            incidentRepository.findByTarget_IdAndStatus(targetId, "OPEN").forEach { incident ->
                val code = incident.reasonCode ?: return@forEach
                if (code !in POLL_CLEARABLE_REASON_CODES) return@forEach
                if (incident.dedupKey in activeDedupKeys) return@forEach
                resolveByDedupKeyLocked(incident.dedupKey, "poll signal cleared")
            }
        }
    }

    fun resolveByDedupKey(dedupKey: String, details: String?): Boolean {
        val existing = incidentRepository.findByDedupKeyAndStatus(dedupKey, "OPEN").orElse(null)
            ?: return false
        val lockKey = existing.target?.id ?: -1L
        val lock = locks.computeIfAbsent(lockKey) { Any() }
        synchronized(lock) {
            return resolveByDedupKeyLocked(dedupKey, details)
        }
    }

    private fun resolveByDedupKeyLocked(dedupKey: String, details: String?): Boolean {
        val incident = incidentRepository.findByDedupKeyAndStatus(dedupKey, "OPEN").orElse(null)
            ?: return false
        incident.status = "RESOLVED"
        incident.resolvedAt = Instant.now()
        incidentRepository.save(incident)
        recordEvent(incident, "CLEARED", details, incident.reasonCode, incident.target?.id)
        summaryCache.invalidate()
        return true
    }

    private fun evaluateLocked(targetId: Long?, signals: List<AlertSignal>): AlertEvaluationResult {
        val decisions = mutableListOf<String>()
        val opened = mutableListOf<Long>()
        var suppressed = false
        val target = targetId?.let { targetRepository.findById(it).orElse(null) }

        signals.forEach { signal ->
            if (targetId != null) {
                val ancestor = correlationEngine.findSuppressingAncestorIncident(targetId, signal.reasonCode)
                if (ancestor != null) {
                    ancestor.id?.let { recordSuppression(it, targetId, signal.reasonCode) }
                    decisions += "SUPPRESSED"
                    suppressed = true
                    return@forEach
                }
            }

            val existing = incidentRepository.findByDedupKeyAndStatus(signal.dedupKey, "OPEN")
            if (existing.isPresent) {
                val incident = existing.get()
                recordEvent(incident, "ALERT_SEEN", signal.details, signal.reasonCode, targetId)
                decisions += "CONTINUE"
                notifier.notifyIfNeeded(incident)
                return@forEach
            }

            try {
                val incident = NetDiagIncident(
                    target = target,
                    dedupKey = signal.dedupKey,
                    status = "OPEN",
                    severity = signal.severity,
                    title = signal.title,
                    reasonCode = signal.reasonCode,
                    openedAt = Instant.now()
                )
                val saved = incidentRepository.save(incident)
                recordEvent(saved, "OPENED", signal.details, signal.reasonCode, targetId)
                decisions += "OPEN"
                saved.id?.let { opened += it }
                summaryCache.invalidate()
                notifier.notifyIfNeeded(saved)
                llmWebhookService.notifyIncidentOpened(saved)
            } catch (_: DataIntegrityViolationException) {
                val raced = incidentRepository.findByDedupKeyAndStatus(signal.dedupKey, "OPEN").orElse(null)
                if (raced != null) {
                    recordEvent(raced, "ALERT_SEEN", signal.details, signal.reasonCode, targetId)
                    decisions += "CONTINUE"
                    notifier.notifyIfNeeded(raced)
                }
            }
        }

        return AlertEvaluationResult(
            decisions = decisions,
            openedIncidentIds = opened,
            suppressed = suppressed
        )
    }

    private fun recordEvent(
        incident: NetDiagIncident,
        type: String,
        payload: String?,
        reasonCode: String?,
        targetId: Long?
    ) {
        incidentEventRepository.save(
            NetDiagIncidentEvent(
                incident = incident,
                type = type,
                payload = payload,
                reasonCode = reasonCode,
                targetId = targetId,
                createdAt = Instant.now()
            )
        )
    }

    private fun recordSuppression(incidentId: Long, targetId: Long, reasonCode: String) {
        val now = Instant.now()
        val windowStart = alignToWindow(now)
        if (suppressionWindowRepository.incrementWindow(incidentId, targetId, reasonCode, windowStart, now) > 0) {
            return
        }
        try {
            suppressionWindowRepository.save(
                NetDiagAlertSuppressionWindow(
                    incidentId = incidentId,
                    targetId = targetId,
                    reasonCode = reasonCode,
                    windowStart = windowStart,
                    eventCount = 1,
                    firstSeenAt = now,
                    lastSeenAt = now
                )
            )
        } catch (_: DataIntegrityViolationException) {
            suppressionWindowRepository.incrementWindow(incidentId, targetId, reasonCode, windowStart, now)
        }
    }

    private fun alignToWindow(at: Instant): Instant {
        val windowSeconds = Duration.ofMinutes(
            properties.alert.suppressionWindowMinutes.coerceAtLeast(1).toLong()
        ).seconds
        return Instant.ofEpochSecond(at.epochSecond / windowSeconds * windowSeconds)
    }

    companion object {
        val POLL_CLEARABLE_REASON_CODES = setOf(
            "LINK_DOWN",
            "GRE_TUNNEL_DOWN",
            "OPTICAL_RX_LOW",
            "OPTICAL_TX_FAULT",
            "POLL_STALE",
            "UPSTREAM_PROBE_FAIL",
            "PSU_FAIL",
            "FAN_FAIL",
            "LOW_VOLTAGE",
            "FIRMWARE_DRIFT",
            "CPU_HIGH",
            "UNEXPECTED_REBOOT",
            "DEVICE_UNREACHABLE",
            "AUTH_FAILURE",
            "TIMEOUT",
            "COMMAND_ERROR",
            "DEVICE_NOT_FOUND"
        )
    }
}
