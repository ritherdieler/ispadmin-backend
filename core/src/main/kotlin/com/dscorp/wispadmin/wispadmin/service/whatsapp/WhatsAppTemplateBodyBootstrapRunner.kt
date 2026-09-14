package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppSyncedTemplateRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class WhatsAppTemplateBodyBootstrapRunner(
    private val whatsAppProperties: WhatsAppProperties,
    private val syncedTemplateRepository: WhatsAppSyncedTemplateRepository,
    private val templateSyncService: WhatsAppTemplateSyncService
) : ApplicationRunner {

    override fun run(args: ApplicationArguments?) {
        if (!whatsAppProperties.isConfigured()) return
        val catalogNames = WhatsAppTemplateCatalog.all().map { it.metaName }
        val needsSync = catalogNames.any { name ->
            syncedTemplateRepository.findByName(name)?.bodyText.isNullOrBlank()
        }
        if (needsSync) {
            runCatching { templateSyncService.syncFromMeta() }
        }
    }
}
