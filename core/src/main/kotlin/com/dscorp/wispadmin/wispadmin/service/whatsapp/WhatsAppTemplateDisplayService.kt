package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppSyncedTemplateRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class WhatsAppTemplateDisplayService(
    private val syncedTemplateRepository: WhatsAppSyncedTemplateRepository,
    private val templateSyncService: WhatsAppTemplateSyncService,
    private val whatsAppProperties: WhatsAppProperties
) {
    private val bodyRenderer = WhatsAppTemplateBodyRenderer()
    private val log = LoggerFactory.getLogger(WhatsAppTemplateDisplayService::class.java)

    @Volatile
    private var catalogSyncAttempted = false

    fun bodyTextForMetaName(metaName: String): String? = resolveBodyText(metaName)

    fun buildLogPreview(
        definition: WhatsAppTemplateDefinition,
        parameters: List<NamedTemplateParameter>
    ): String {
        val bodyText = resolveBodyText(definition.metaName)
        if (!bodyText.isNullOrBlank()) {
            return bodyRenderer.render(bodyText, parameters)
        }
        log.warn(
            "BODY de plantilla {} no disponible tras sync; usando texto fallback para el log.",
            definition.metaName
        )
        return WhatsAppTemplateHumanFallback.render(definition, parameters)
    }

    fun displayStoredMessage(storedMessage: String?, messageType: String?): String? {
        if (storedMessage.isNullOrBlank()) return storedMessage
        return bodyRenderer.buildDisplayMessage(
            storedMessage = storedMessage,
            messageType = messageType,
            bodyTextForMetaName = ::resolveBodyText
        )
    }

    fun displayStoredMessages(messages: List<Pair<String?, String?>>): List<String?> {
        if (messages.isEmpty()) return emptyList()
        val metaNames = messages.mapNotNull { (stored, _) ->
            stored?.let { bodyRenderer.parseLegacyPreview(it)?.first }
        }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val bodiesByName = loadBodiesByName(metaNames)
        return messages.map { (stored, type) ->
            if (stored.isNullOrBlank()) return@map stored
            bodyRenderer.buildDisplayMessage(
                storedMessage = stored,
                messageType = type,
                bodyTextForMetaName = { metaName ->
                    val key = metaName.trim()
                    if (key.isEmpty()) return@buildDisplayMessage null
                    bodiesByName[key] ?: resolveBodyText(key)
                }
            )
        }
    }

    fun resolveBodyText(metaName: String): String? {
        val key = metaName.trim()
        if (key.isEmpty()) return null
        readBodyFromDb(key)?.let { return it }
        triggerCatalogSyncIfNeeded()
        return readBodyFromDb(key)
    }

    private fun loadBodiesByName(metaNames: Collection<String>): Map<String, String?> {
        if (metaNames.isEmpty()) return emptyMap()
        val found = syncedTemplateRepository.findByNameIn(metaNames.toList()).associate { template ->
            template.name to template.bodyText?.trim()?.takeIf { it.isNotEmpty() }
        }
        return metaNames.associateWith { name -> found[name] }
    }

    private fun readBodyFromDb(metaName: String): String? =
        syncedTemplateRepository.findByName(metaName)?.bodyText?.trim()?.takeIf { it.isNotEmpty() }

    private fun triggerCatalogSyncIfNeeded() {
        if (!whatsAppProperties.isConfigured()) return
        if (catalogSyncAttempted) return
        synchronized(this) {
            if (catalogSyncAttempted) return
            catalogSyncAttempted = true
            runCatching { templateSyncService.syncFromMeta() }
                .onFailure { log.warn("No se pudo sincronizar plantillas WhatsApp desde Meta: {}", it.message) }
        }
    }
}
