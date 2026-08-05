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

        templateAnalyticsGroups(root).forEach { entry ->
            val entryTemplateId = entry.path("template_id").asText(null)
            entry.path("data_points").forEach { point ->
                val templateId = point.path("template_id").asText(null)
                    ?: entryTemplateId
                    ?: return@forEach
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
        }.sortedByDescending { it.sent + it.clicked }
    }

    private fun templateAnalyticsGroups(root: JsonNode): List<JsonNode> {
        val groups = mutableListOf<JsonNode>()
        val nested = root.path("template_analytics").path("data")
        when {
            nested.isArray -> nested.forEach { groups.add(it) }
            nested.isObject && !nested.isMissingNode -> groups.add(nested)
        }
        if (root.path("data").isArray) {
            root.path("data").forEach { groups.add(it) }
        }
        return groups
    }

    fun parseConversationAnalytics(root: JsonNode): WhatsAppMetaConversationAnalyticsDto {
        val categories = linkedMapOf<String, ConversationAccumulator>()
        val groups = conversationAnalyticsGroups(root)

        groups.forEach { entry ->
            entry.path("data_points").forEach { point ->
                val category = point.path("conversation_category").asText(
                    point.path("category").asText(
                        point.path("conversation_type").asText("UNKNOWN")
                    )
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
        val aggregated = linkedMapOf<String, PricingAccumulator>()

        pricingAnalyticsGroups(root).forEach { entry ->
            entry.path("data_points").forEach { point ->
                val pricingCategory = point.path("pricing_category").asText(null)
                val pricingType = point.path("pricing_type").asText(null)
                val country = point.path("country").asText(null)
                val tierRaw = point.path("tier").asText(null)
                val tier = when {
                    !tierRaw.isNullOrBlank() -> tierRaw
                    !pricingType.isNullOrBlank() -> pricingType
                    else -> "—"
                }
                val category = when {
                    !pricingCategory.isNullOrBlank() -> pricingCategory
                    !country.isNullOrBlank() -> country
                    else -> "Agregado"
                }
                val key = listOf(tier, category, country.orEmpty(), pricingType.orEmpty()).joinToString("|")
                val bucket = aggregated.getOrPut(key) {
                    PricingAccumulator(tier = tier, category = category)
                }
                bucket.volume += point.path("volume").asInt(
                    point.path("conversation_count").asInt(0)
                )
                if (point.has("cost")) {
                    bucket.cost = (bucket.cost ?: 0.0) + point.path("cost").asDouble(0.0)
                }
                bucket.currency = point.path("currency").asText(bucket.currency)
            }
        }

        val tiers = aggregated.values.map {
            WhatsAppMetaPricingTierDto(
                tier = it.tier,
                category = it.category,
                volume = it.volume,
                cost = it.cost,
                currency = it.currency
            )
        }.sortedByDescending { it.cost ?: 0.0 }

        return WhatsAppMetaPricingAnalyticsDto(tiers = tiers)
    }

    private fun pricingAnalyticsGroups(root: JsonNode): List<JsonNode> {
        val groups = mutableListOf<JsonNode>()
        val pricingData = root.path("pricing_analytics").path("data")
        when {
            pricingData.isArray -> pricingData.forEach { groups.add(it) }
            pricingData.isObject && !pricingData.isMissingNode -> groups.add(pricingData)
        }
        if (groups.isEmpty() && root.path("data").isArray) {
            root.path("data").forEach { groups.add(it) }
        }
        return groups
    }

    private fun conversationAnalyticsGroups(root: JsonNode): List<JsonNode> {
        val groups = mutableListOf<JsonNode>()
        val conversationData = root.path("conversation_analytics").path("data")
        when {
            conversationData.isArray -> conversationData.forEach { groups.add(it) }
            conversationData.isObject && !conversationData.isMissingNode -> groups.add(conversationData)
        }
        if (groups.isEmpty() && root.path("data").isArray) {
            root.path("data").forEach { groups.add(it) }
        }
        return groups
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

    private data class PricingAccumulator(
        val tier: String,
        val category: String,
        var volume: Int = 0,
        var cost: Double? = null,
        var currency: String? = null
    )
}
