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

    fun resolveBodyText(metaName: String): String? {
        readBodyFromDb(metaName)?.let { return it }
        triggerCatalogSyncIfNeeded()
        return readBodyFromDb(metaName)
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
