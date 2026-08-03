package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppSyncedTemplate
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppSyncedTemplateRepository
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class WhatsAppTemplateSyncService(
    private val metaAnalyticsClient: WhatsAppMetaAnalyticsClient,
    private val syncedTemplateRepository: WhatsAppSyncedTemplateRepository
) {

    fun syncFromMeta(): WhatsAppTemplateSyncResult {
        val response = metaAnalyticsClient.fetchMessageTemplates()
        val data = response.path("data")
        if (!data.isArray) {
            return WhatsAppTemplateSyncResult(synced = 0, created = 0, updated = 0, errors = emptyList())
        }

        var created = 0
        var updated = 0
        val errors = mutableListOf<String>()
        val synced = data.mapNotNull { node ->
            runCatching {
                val id = node.path("id").asText("")
                if (id.isBlank()) {
                    errors.add("Plantilla sin id: ${node.path("name").asText("?")}")
                    return@mapNotNull null
                }
                val existing = syncedTemplateRepository.findById(id).orElse(null)
                val template = syncTemplate(node)
                if (existing == null) created++ else updated++
                template
            }.getOrElse {
                errors.add(it.message ?: "Error sincronizando plantilla")
                null
            }
        }
        syncedTemplateRepository.saveAll(synced)
        return WhatsAppTemplateSyncResult(
            synced = synced.size,
            created = created,
            updated = updated,
            errors = errors,
            templates = synced.map { it.toSummary() }
        )
    }

    fun listSynced(): List<WhatsAppSyncedTemplateSummary> {
        return syncedTemplateRepository.findAllByOrderByNameAsc().map { it.toSummary() }
    }

    fun findMetaTemplateIdByName(name: String): String? {
        return syncedTemplateRepository.findByName(name)?.metaTemplateId
    }

    private fun syncTemplate(node: JsonNode): WhatsAppSyncedTemplate {
        val id = node.path("id").asText("")
        return WhatsAppSyncedTemplate(
            metaTemplateId = id,
            name = node.path("name").asText(""),
            status = node.path("status").asText(null),
            category = node.path("category").asText(null),
            qualityScore = node.path("quality_score").path("score").asText(
                node.path("quality_score").asText(null)
            ),
            language = node.path("language").asText(null),
            bodyText = extractBodyText(node),
            syncedAt = LocalDateTime.now()
        )
    }

    private fun WhatsAppSyncedTemplate.toSummary() = WhatsAppSyncedTemplateSummary(
        metaTemplateId = metaTemplateId,
        name = name,
        status = status,
        category = category,
        qualityScore = qualityScore,
        language = language,
        bodyText = bodyText,
        syncedAt = syncedAt
    )

    data class WhatsAppTemplateSyncResult(
        val synced: Int,
        val created: Int,
        val updated: Int,
        val errors: List<String>,
        val templates: List<WhatsAppSyncedTemplateSummary> = emptyList()
    )

    data class WhatsAppSyncedTemplateSummary(
        val metaTemplateId: String,
        val name: String,
        val status: String?,
        val category: String?,
        val qualityScore: String?,
        val language: String?,
        val bodyText: String?,
        val syncedAt: LocalDateTime
    )

    companion object {
        fun extractBodyText(node: JsonNode): String? {
            val components = node.path("components")
            if (!components.isArray) return null
            for (component in components) {
                if (component.path("type").asText("").equals("BODY", ignoreCase = true)) {
                    val text = component.path("text").asText("").trim()
                    if (text.isNotEmpty()) return text
                }
            }
            return null
        }
    }
}
