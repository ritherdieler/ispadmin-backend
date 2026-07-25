package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppAnalyticsService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppAccountEventService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppServiceWindowService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateSyncService
import java.time.LocalDateTime

data class WhatsAppAnalyticsOverviewDto(
    val sent: Int,
    val delivered: Int,
    val read: Int,
    val failed: Int,
    val skipped: Int,
    val responded: Int,
    val paid: Int,
    val deliveryRate: Double,
    val readRate: Double,
    val responseRate: Double,
    val conversionRate: Double,
    val recoveredAmount: Double,
    val missingMetaMessageIdCount: Int,
    val periodDays: Int
)

fun WhatsAppAnalyticsService.WhatsAppAnalyticsOverview.toDto() = WhatsAppAnalyticsOverviewDto(
    sent = sent,
    delivered = delivered,
    read = read,
    failed = failed,
    skipped = skipped,
    responded = responded,
    paid = paid,
    deliveryRate = deliveryRate,
    readRate = readRate,
    responseRate = responseRate,
    conversionRate = conversionRate,
    recoveredAmount = recoveredAmount,
    missingMetaMessageIdCount = sentWithoutMetaMessageId,
    periodDays = periodDays
)

data class WhatsAppCampaignSummaryDto(
    val id: String,
    val templateCode: String?,
    val templateLabel: String?,
    val sent: Int,
    val delivered: Int,
    val read: Int,
    val failed: Int,
    val responded: Int,
    val paid: Int,
    val operatorName: String?,
    val createdAt: String?,
    val conversionAmount: Double? = null
)

fun WhatsAppAnalyticsService.WhatsAppCampaignAnalytics.toSummaryDto() = WhatsAppCampaignSummaryDto(
    id = campaignId,
    templateCode = templateCode,
    templateLabel = templateLabel,
    sent = sent,
    delivered = delivered,
    read = read,
    failed = failed,
    responded = responded,
    paid = paid,
    operatorName = operatorUsername,
    createdAt = startedAt?.toString(),
    conversionAmount = conversionAmount
)

data class WhatsAppCampaignDetailDto(
    val id: String,
    val templateCode: String?,
    val templateLabel: String?,
    val sent: Int,
    val delivered: Int,
    val read: Int,
    val failed: Int,
    val responded: Int,
    val paid: Int,
    val operatorName: String?,
    val createdAt: String?,
    val conversionAmount: Double,
    val details: List<WhatsAppMessageLogDto>
)

fun WhatsAppAnalyticsService.WhatsAppCampaignDetail.toFrontendDto(
    logs: List<WhatsAppMessageLogDto>
) = WhatsAppCampaignDetailDto(
    id = summary?.campaignId ?: "",
    templateCode = summary?.templateCode,
    templateLabel = summary?.templateLabel,
    sent = summary?.sent ?: 0,
    delivered = summary?.delivered ?: 0,
    read = summary?.read ?: 0,
    failed = summary?.failed ?: 0,
    responded = summary?.responded ?: 0,
    paid = summary?.paid ?: 0,
    operatorName = summary?.operatorUsername,
    createdAt = summary?.startedAt?.toString(),
    conversionAmount = summary?.conversionAmount ?: 0.0,
    details = logs
)

data class WhatsAppConversionByTemplateDto(
    val templateCode: String,
    val templateLabel: String?,
    val sent: Int,
    val converted: Int,
    val conversionRate: Double,
    val recoveredAmount: Double
)

data class WhatsAppConversionMetricsDto(
    val periodDays: Int,
    val convertedCount: Int,
    val conversionRate: Double,
    val recoveredAmount: Double,
    val byTemplate: List<WhatsAppConversionByTemplateDto>
)

fun WhatsAppAnalyticsService.WhatsAppConversionAnalytics.toFrontendDto() = WhatsAppConversionMetricsDto(
    periodDays = windowDays,
    convertedCount = converted,
    conversionRate = conversionRate,
    recoveredAmount = recoveredAmount,
    byTemplate = byTemplate.map {
        WhatsAppConversionByTemplateDto(
            templateCode = it.templateCode,
            templateLabel = it.templateLabel,
            sent = it.sent,
            converted = it.converted,
            conversionRate = it.conversionRate,
            recoveredAmount = it.recoveredAmount
        )
    }
)

data class WhatsAppAccountAlertDto(
    val type: String,
    val message: String,
    val severity: String?
)

data class WhatsAppPausedTemplateDto(
    val code: String,
    val name: String,
    val reason: String?
)

data class WhatsAppAccountHealthDto(
    val qualityScore: String,
    val messagingLimit: String? = null,
    val messagingLimitTier: String? = null,
    val pausedTemplates: List<WhatsAppPausedTemplateDto>,
    val alerts: List<WhatsAppAccountAlertDto>,
    val phoneQuality: String? = null,
    val lastSyncedAt: String? = null
)

fun WhatsAppAccountEventService.WhatsAppAccountHealthSummary.toFrontendDto() = WhatsAppAccountHealthDto(
    qualityScore = qualityScore ?: "GREEN",
    messagingLimit = messagingLimit,
    messagingLimitTier = messagingLimitTier,
    pausedTemplates = pausedTemplates,
    alerts = alerts,
    phoneQuality = phoneQuality ?: qualityScore,
    lastSyncedAt = lastSyncedAt?.toString()
)

data class WhatsAppServiceWindowDto(
    val phone: String,
    val open: Boolean,
    val expiresAt: LocalDateTime?,
    val lastInboundAt: LocalDateTime? = null
)

fun WhatsAppServiceWindowService.WhatsAppServiceWindowStatus.toDto() = WhatsAppServiceWindowDto(
    phone = phone,
    open = open,
    expiresAt = expiresAt,
    lastInboundAt = lastInboundAt
)

data class WhatsAppSyncedTemplateDto(
    val metaTemplateId: String,
    val name: String,
    val status: String?,
    val category: String?,
    val qualityScore: String?,
    val language: String?,
    val syncedAt: LocalDateTime
)

fun WhatsAppTemplateSyncService.WhatsAppSyncedTemplateSummary.toDto() = WhatsAppSyncedTemplateDto(
    metaTemplateId = metaTemplateId,
    name = name,
    status = status,
    category = category,
    qualityScore = qualityScore,
    language = language,
    syncedAt = syncedAt
)

data class WhatsAppTemplateSyncResultDto(
    val synced: Int,
    val created: Int,
    val updated: Int,
    val errors: List<String>
)

fun WhatsAppTemplateSyncService.WhatsAppTemplateSyncResult.toFrontendDto() = WhatsAppTemplateSyncResultDto(
    synced = synced,
    created = created,
    updated = updated,
    errors = errors
)

data class WhatsAppMarkReadResultDto(
    val success: Boolean,
    val inboundMessageId: Int,
    val metaMessageId: String
)

data class WhatsAppMetaTemplateAnalyticsItemDto(
    val templateId: String,
    val templateName: String,
    val sent: Int,
    val delivered: Int,
    val read: Int,
    val clicked: Int
)

data class WhatsAppMetaConversationCategoryDto(
    val category: String,
    val conversationCount: Int,
    val cost: Double?,
    val currency: String?
)

data class WhatsAppMetaConversationAnalyticsDto(
    val categories: List<WhatsAppMetaConversationCategoryDto>,
    val totalCost: Double?,
    val currency: String?
)

data class WhatsAppMetaPricingTierDto(
    val tier: String,
    val category: String,
    val volume: Int,
    val cost: Double?,
    val currency: String?
)

data class WhatsAppMetaPricingAnalyticsDto(
    val tiers: List<WhatsAppMetaPricingTierDto>
)
