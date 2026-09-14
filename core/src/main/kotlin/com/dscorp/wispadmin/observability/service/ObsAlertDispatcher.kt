package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.dscorp.wispadmin.observability.entity.ObsAlertEvent
import com.dscorp.wispadmin.observability.entity.ObsAlertRule
import com.dscorp.wispadmin.observability.port.AlertChannelRegistry
import com.dscorp.wispadmin.observability.port.AlertNotification
import com.dscorp.wispadmin.observability.repository.ObsAlertChannelRepository
import com.dscorp.wispadmin.observability.repository.ObsAlertEventRepository
import com.dscorp.wispadmin.observability.repository.ObsAlertRuleRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ObsAlertDispatcher(
    private val channelRepository: ObsAlertChannelRepository,
    private val alertEventRepository: ObsAlertEventRepository,
    private val ruleRepository: ObsAlertRuleRepository,
    private val channelRegistry: AlertChannelRegistry,
    private val properties: ObservabilityProperties
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    @Transactional
    fun dispatch(
        rule: ObsAlertRule,
        title: String,
        message: String,
        dedupKey: String,
        issueId: Long? = null,
        observedValue: Double? = null,
        thresholdValue: Double? = null
    ) {
        try {
            val since = LocalDateTime.now().minusMinutes(properties.alerts.dedupWindowMinutes)
            if (alertEventRepository.countRecentByDedupKey(dedupKey, since) > 0) {
                return
            }

            val issueUrl = buildIssueUrl(issueId)
            val notification = AlertNotification(
                title = title,
                message = message,
                severity = rule.severity,
                type = rule.type.name,
                issueUrl = issueUrl
            )

            val channelIds = rule.channelIds
                ?.split(",")
                ?.mapNotNull { it.trim().toLongOrNull() }
                ?: emptyList()

            val details = mutableListOf<String>()
            var delivered = false
            channelIds.forEach { channelId ->
                val channel = channelRepository.findById(channelId).orElse(null) ?: return@forEach
                if (!channel.enabled) return@forEach
                val adapter = channelRegistry.forType(channel.type)
                if (adapter == null) {
                    details.add("${channel.name}: canal no soportado")
                    return@forEach
                }
                val result = adapter.send(channel, notification)
                if (result.ok) delivered = true
                details.add("${channel.name}: ${result.detail}")
            }

            alertEventRepository.save(
                ObsAlertEvent(
                    ruleId = rule.id,
                    ruleName = rule.name,
                    type = rule.type,
                    title = title,
                    message = message,
                    severity = rule.severity,
                    issueId = issueId,
                    observedValue = observedValue,
                    thresholdValue = thresholdValue,
                    dedupKey = dedupKey,
                    delivered = delivered,
                    deliveryDetail = details.joinToString("; ").take(1000),
                    createdAt = LocalDateTime.now()
                )
            )

            rule.id?.let {
                ruleRepository.findById(it).ifPresent { existing ->
                    existing.lastTriggeredAt = LocalDateTime.now()
                    ruleRepository.save(existing)
                }
            }
        } catch (e: Exception) {
            log.warn("No se pudo despachar alerta '{}': {}", rule.name, e.message)
        }
    }

    private fun buildIssueUrl(issueId: Long?): String? {
        if (issueId == null) return null
        val base = properties.alerts.dashboardBaseUrl.trimEnd('/')
        if (base.isBlank()) return null
        return "$base/observability/issues?issueId=$issueId"
    }
}
