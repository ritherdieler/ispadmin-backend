package com.dscorp.wispadmin.observability.dto

import com.dscorp.wispadmin.observability.entity.ObsAlertChannel
import com.dscorp.wispadmin.observability.entity.ObsAlertChannelType
import com.dscorp.wispadmin.observability.entity.ObsAlertComparator
import com.dscorp.wispadmin.observability.entity.ObsAlertEvent
import com.dscorp.wispadmin.observability.entity.ObsAlertRule
import com.dscorp.wispadmin.observability.entity.ObsAlertType
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDateTime

data class AlertRuleDto(
    val id: Long?,
    val name: String,
    val type: ObsAlertType,
    val enabled: Boolean,
    val platform: String?,
    val severity: String?,
    val environment: String?,
    val routePattern: String?,
    val comparator: ObsAlertComparator,
    val threshold: Double,
    val windowMinutes: Int,
    val baselineMultiplier: Double,
    val minSample: Long,
    val channelIds: List<Long>,
    val lastTriggeredAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AlertRuleUpsertRequest(
    val name: String? = null,
    val type: ObsAlertType? = null,
    val enabled: Boolean? = null,
    val platform: String? = null,
    val severity: String? = null,
    val environment: String? = null,
    val routePattern: String? = null,
    val comparator: ObsAlertComparator? = null,
    val threshold: Double? = null,
    val windowMinutes: Int? = null,
    val baselineMultiplier: Double? = null,
    val minSample: Long? = null,
    val channelIds: List<Long>? = null
)

data class AlertChannelDto(
    val id: Long?,
    val name: String,
    val type: ObsAlertChannelType,
    val enabled: Boolean,
    val target: String,
    val hasConfig: Boolean,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AlertChannelUpsertRequest(
    val name: String? = null,
    val type: ObsAlertChannelType? = null,
    val enabled: Boolean? = null,
    val target: String? = null,
    val configJson: String? = null
)

data class AlertEventDto(
    val id: Long?,
    val ruleId: Long?,
    val ruleName: String?,
    val type: ObsAlertType,
    val title: String?,
    val message: String?,
    val severity: String?,
    val issueId: Long?,
    val observedValue: Double?,
    val thresholdValue: Double?,
    val delivered: Boolean,
    val deliveryDetail: String?,
    val createdAt: LocalDateTime?
)

data class TestChannelResult(
    val ok: Boolean,
    val detail: String?
)

private fun parseChannelIds(raw: String?): List<Long> =
    raw?.split(",")
        ?.mapNotNull { it.trim().toLongOrNull() }
        ?: emptyList()

fun ObsAlertRule.toDto() = AlertRuleDto(
    id = id,
    name = name,
    type = type,
    enabled = enabled,
    platform = platform,
    severity = severity,
    environment = environment,
    routePattern = routePattern,
    comparator = comparator,
    threshold = threshold,
    windowMinutes = windowMinutes,
    baselineMultiplier = baselineMultiplier,
    minSample = minSample,
    channelIds = parseChannelIds(channelIds),
    lastTriggeredAt = lastTriggeredAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun ObsAlertChannel.toDto() = AlertChannelDto(
    id = id,
    name = name,
    type = type,
    enabled = enabled,
    target = target,
    hasConfig = !configJson.isNullOrBlank(),
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun ObsAlertEvent.toDto() = AlertEventDto(
    id = id,
    ruleId = ruleId,
    ruleName = ruleName,
    type = type,
    title = title,
    message = message,
    severity = severity,
    issueId = issueId,
    observedValue = observedValue,
    thresholdValue = thresholdValue,
    delivered = delivered,
    deliveryDetail = deliveryDetail,
    createdAt = createdAt
)
