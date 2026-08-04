package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppAccountEvent
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppAccountAlertDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppPausedTemplateDto
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppAccountEventRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppSyncedTemplateRepository
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class WhatsAppAccountEventService(
    private val accountEventRepository: WhatsAppAccountEventRepository,
    private val messageLogRepository: WhatsAppMessageLogRepository,
    private val syncedTemplateRepository: WhatsAppSyncedTemplateRepository,
    private val metaAnalyticsClient: WhatsAppMetaAnalyticsClient
) {

    companion object {
        private const val NEAR_LIMIT_THRESHOLD = 0.8
    }

    fun recordManagementEvent(
        field: String,
        value: JsonNode,
        eventKeySuffix: String = ""
    ): WhatsAppAccountEvent? {
        val eventKey = buildEventKey(field, value, eventKeySuffix)
        if (accountEventRepository.existsByEventKey(eventKey)) return null

        val event = WhatsAppAccountEvent(
            eventType = field,
            eventKey = eventKey,
            templateName = extractTemplateName(value),
            templateId = extractTemplateId(value),
            severity = extractSeverity(value),
            qualityScore = extractQualityScore(value, field),
            payloadSummary = value.toString().take(4000),
            createdAt = LocalDateTime.now()
        )
        return accountEventRepository.save(event)
    }

    fun getAccountHealth(): WhatsAppAccountHealthSummary {
        val recent = accountEventRepository.findTop20ByOrderByCreatedAtDesc()
        val qualityEvents = recent.filter {
            it.eventType.contains("quality", ignoreCase = true) ||
                it.eventType.contains("template", ignoreCase = true)
        }
        val alertEvents = recent.filter {
            it.eventType == "account_alerts" ||
                it.severity?.equals("CRITICAL", ignoreCase = true) == true
        }
        val latestQuality = qualityEvents.firstOrNull()?.qualityScore
        val pausedTemplateEvents = recent.filter {
            it.eventType == "message_template_status_update" &&
                (it.payloadSummary?.contains("PAUSED", ignoreCase = true) == true ||
                    it.payloadSummary?.contains("DISABLED", ignoreCase = true) == true)
        }
        val pausedTemplates = pausedTemplateEvents.mapNotNull { event ->
            val name = event.templateName ?: return@mapNotNull null
            WhatsAppPausedTemplateDto(
                code = name,
                name = name,
                reason = event.payloadSummary?.let { summary ->
                    if (summary.contains("PAUSED", ignoreCase = true)) "PAUSED" else "DISABLED"
                }
            )
        }.distinctBy { it.code }

        val phoneHealthNode = metaAnalyticsClient.fetchPhoneNumberHealth()
        val messagingLimitTier = WhatsAppMetaAnalyticsClient.parseMessagingLimitTier(phoneHealthNode)
        val messagingLimit = WhatsAppMessagingLimitTiers.dailyLimitFor(messagingLimitTier)
        val liveQualityRating = WhatsAppMetaAnalyticsClient.parseQualityRating(phoneHealthNode)
        val resolvedQuality = latestQuality ?: liveQualityRating

        val since = LocalDateTime.now().minusHours(24)
        val recentLogs = messageLogRepository.findByCreatedAtBetween(since, LocalDateTime.now())
        val messagingUsedToday = recentLogs.count { it.status == WhatsAppTemplateDeliveryService.STATUS_SENT }

        val operationalAlerts = buildOperationalAlerts(resolvedQuality, recentLogs, messagingUsedToday, messagingLimit)
        val webhookAlerts = alertEvents.map {
            WhatsAppAccountAlertDto(
                type = it.eventType,
                message = it.templateName ?: it.payloadSummary?.take(200) ?: "Alerta de cuenta Meta",
                severity = it.severity
            )
        }

        val lastSynced = syncedTemplateRepository.findAllByOrderByNameAsc()
            .maxOfOrNull { it.syncedAt }

        return WhatsAppAccountHealthSummary(
            qualityScore = resolvedQuality,
            messagingLimit = messagingLimit,
            messagingLimitTier = messagingLimitTier,
            messagingUsedToday = messagingUsedToday,
            phoneQuality = resolvedQuality,
            lastSyncedAt = lastSynced,
            pausedTemplates = pausedTemplates,
            alerts = operationalAlerts + webhookAlerts
        )
    }

    private fun buildOperationalAlerts(
        qualityScore: String?,
        recentLogs: List<WhatsAppMessageLog>,
        messagingUsedToday: Int,
        messagingLimit: Int?
    ): List<WhatsAppAccountAlertDto> {
        val alerts = mutableListOf<WhatsAppAccountAlertDto>()
        when (qualityScore?.uppercase()) {
            "YELLOW" -> alerts.add(
                WhatsAppAccountAlertDto(
                    type = "QUALITY_YELLOW",
                    message = "Calidad de plantilla en YELLOW — revisar contenido y tasas de bloqueo.",
                    severity = "WARNING"
                )
            )
            "RED" -> alerts.add(
                WhatsAppAccountAlertDto(
                    type = "QUALITY_RED",
                    message = "Calidad de plantilla en RED — riesgo de pausa o limitación de envíos.",
                    severity = "CRITICAL"
                )
            )
        }

        val failed = recentLogs.count {
            it.status == WhatsAppTemplateDeliveryService.STATUS_FAILED ||
                it.deliveryStatus == "failed" ||
                it.failedAt != null
        }
        if (messagingUsedToday >= 10) {
            val failureRate = (failed.toDouble() / messagingUsedToday.toDouble()) * 100.0
            if (failureRate >= 20.0) {
                alerts.add(
                    WhatsAppAccountAlertDto(
                        type = "HIGH_FAILURE_RATE",
                        message = "Tasa de fallo alta en las últimas 24h: ${"%.1f".format(failureRate)}% " +
                            "($failed/$messagingUsedToday).",
                        severity = if (failureRate >= 40.0) "CRITICAL" else "WARNING"
                    )
                )
            }
        }

        if (messagingLimit != null && messagingLimit > 0) {
            val usageRatio = messagingUsedToday.toDouble() / messagingLimit.toDouble()
            if (messagingUsedToday >= messagingLimit) {
                alerts.add(
                    WhatsAppAccountAlertDto(
                        type = "MESSAGING_LIMIT_REACHED",
                        message = "Límite diario de conversaciones alcanzado: $messagingUsedToday/$messagingLimit. " +
                            "Los nuevos envíos serán rechazados por Meta hasta el próximo ciclo de 24h.",
                        severity = "CRITICAL"
                    )
                )
            } else if (usageRatio >= NEAR_LIMIT_THRESHOLD) {
                alerts.add(
                    WhatsAppAccountAlertDto(
                        type = "MESSAGING_LIMIT_NEAR",
                        message = "Uso cercano al límite diario de conversaciones: " +
                            "$messagingUsedToday/$messagingLimit.",
                        severity = "WARNING"
                    )
                )
            }
        }

        return alerts
    }

    private fun buildEventKey(field: String, value: JsonNode, suffix: String): String {
        val id = value.path("message_template_id").asText(
            value.path("template_id").asText(
                value.path("event").asText(
                    value.path("alert_type").asText(
                        value.path("display_phone_number").asText("")
                    )
                )
            )
        )
        val ts = value.path("timestamp").asText(
            value.path("time").asText(LocalDateTime.now().toString())
        )
        return "$field:$id:$ts:$suffix".take(512)
    }

    private fun extractTemplateName(value: JsonNode): String? {
        return value.path("message_template_name").asText(null)
            ?: value.path("template_name").asText(null)
    }

    private fun extractTemplateId(value: JsonNode): String? {
        return value.path("message_template_id").asText(null)
            ?: value.path("template_id").asText(null)
    }

    private fun extractSeverity(value: JsonNode): String? {
        return value.path("severity").asText(null)
            ?: value.path("alert_type").asText(null)
    }

    private fun extractQualityScore(value: JsonNode, field: String): String? {
        if (field.contains("quality", ignoreCase = true)) {
            return value.path("new_quality_score").asText(
                value.path("quality_score").asText(null)
            )
        }
        return null
    }

    data class WhatsAppAccountHealthSummary(
        val qualityScore: String?,
        val messagingLimit: Int?,
        val messagingLimitTier: String?,
        val messagingUsedToday: Int?,
        val phoneQuality: String?,
        val lastSyncedAt: LocalDateTime?,
        val pausedTemplates: List<WhatsAppPausedTemplateDto>,
        val alerts: List<WhatsAppAccountAlertDto>
    )
}
