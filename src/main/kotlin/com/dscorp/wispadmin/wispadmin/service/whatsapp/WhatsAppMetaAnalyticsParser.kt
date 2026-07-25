package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.dto.WhatsAppMetaConversationAnalyticsDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppMetaConversationCategoryDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppMetaPricingAnalyticsDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppMetaPricingTierDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppMetaTemplateAnalyticsItemDto
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppSyncedTemplateRepository
import com.fasterxml.jackson.databind.JsonNode

object WhatsAppMetaAnalyticsParser {

    fun parseTemplateAnalytics(
        root: JsonNode,
        templateRepository: WhatsAppSyncedTemplateRepository
    ): List<WhatsAppMetaTemplateAnalyticsItemDto> {
        val aggregated = linkedMapOf<String, MutableTemplateMetrics>()
        val dataArray = when {
            root.path("data").isArray -> root.path("data")
            root.path("template_analytics").path("data").isArray -> root.path("template_analytics").path("data")
            else -> root.path("data")
        }

        dataArray.forEach { entry ->
            entry.path("data_points").forEach { point ->
                val templateId = point.path("template_id").asText(null) ?: return@forEach
                val metrics = aggregated.getOrPut(templateId) { MutableTemplateMetrics(templateId) }
                metrics.sent += point.path("sent").asInt(0)
                metrics.delivered += point.path("delivered").asInt(0)
                metrics.read += point.path("read").asInt(0)
                metrics.clicked += sumClicked(point.path("clicked"))
            }
        }

        return aggregated.values.map { metrics ->
            val templateName = templateRepository.findById(metrics.templateId).orElse(null)?.name
                ?: metrics.templateId
            WhatsAppMetaTemplateAnalyticsItemDto(
                templateId = metrics.templateId,
                templateName = templateName,
                sent = metrics.sent,
                delivered = metrics.delivered,
                read = metrics.read,
                clicked = metrics.clicked
            )
        }.sortedByDescending { it.sent }
    }

    fun parseConversationAnalytics(root: JsonNode): WhatsAppMetaConversationAnalyticsDto {
        val categories = linkedMapOf<String, ConversationAccumulator>()
        val dataArray = when {
            root.path("conversation_analytics").path("data").isArray ->
                root.path("conversation_analytics").path("data")
            root.path("data").isArray -> root.path("data")
            else -> root.path("conversation_analytics").path("data")
        }

        dataArray.forEach { entry ->
            entry.path("data_points").forEach { point ->
                val category = point.path("conversation_category").asText(
                    point.path("category").asText("UNKNOWN")
                )
                val bucket = categories.getOrPut(category) { ConversationAccumulator(category) }
                bucket.conversationCount += point.path("conversation").asInt(
                    point.path("conversation_count").asInt(0)
                )
                if (point.has("cost")) {
                    bucket.cost = (bucket.cost ?: 0.0) + point.path("cost").asDouble(0.0)
                }
                bucket.currency = point.path("currency").asText(bucket.currency)
            }
        }

        val items = categories.values.map {
            WhatsAppMetaConversationCategoryDto(
                category = it.category,
                conversationCount = it.conversationCount,
                cost = it.cost,
                currency = it.currency
            )
        }
        val totalCost = items.mapNotNull { it.cost }.takeIf { it.isNotEmpty() }?.sum()
        val currency = items.firstOrNull { !it.currency.isNullOrBlank() }?.currency

        return WhatsAppMetaConversationAnalyticsDto(
            categories = items,
            totalCost = totalCost,
            currency = currency
        )
    }

    fun parsePricingAnalytics(root: JsonNode): WhatsAppMetaPricingAnalyticsDto {
        val tiers = mutableListOf<WhatsAppMetaPricingTierDto>()
        val dataArray = when {
            root.path("pricing_analytics").path("data").isArray ->
                root.path("pricing_analytics").path("data")
            root.path("data").isArray -> root.path("data")
            else -> root.path("pricing_analytics").path("data")
        }

        dataArray.forEach { entry ->
            entry.path("data_points").forEach { point ->
                tiers.add(
                    WhatsAppMetaPricingTierDto(
                        tier = point.path("tier").asText(
                            point.path("pricing_tier").asText("UNKNOWN")
                        ),
                        category = point.path("category").asText(
                            point.path("conversation_category").asText("UNKNOWN")
                        ),
                        volume = point.path("volume").asInt(
                            point.path("conversation_count").asInt(0)
                        ),
                        cost = point.path("cost").takeIf { it.isNumber || it.isTextual }?.asDouble(),
                        currency = point.path("currency").asText(null)
                    )
                )
            }
        }

        return WhatsAppMetaPricingAnalyticsDto(tiers = tiers)
    }

    private fun sumClicked(node: JsonNode): Int {
        if (node.isMissingNode || node.isNull) return 0
        if (node.isNumber) return node.asInt(0)
        if (!node.isArray) return 0
        var total = 0
        node.forEach { item ->
            total += item.path("count").asInt(0)
        }
        return total
    }

    private data class MutableTemplateMetrics(
        val templateId: String,
        var sent: Int = 0,
        var delivered: Int = 0,
        var read: Int = 0,
        var clicked: Int = 0
    )

    private data class ConversationAccumulator(
        val category: String,
        var conversationCount: Int = 0,
        var cost: Double? = null,
        var currency: String? = null
    )
}
