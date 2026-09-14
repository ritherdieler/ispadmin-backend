package com.dscorp.wispadmin.wispadmin.search.infrastructure.meili

import com.dscorp.wispadmin.wispadmin.search.application.SubscriptionSearchReindexer
import com.meilisearch.sdk.Client
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import java.util.concurrent.CompletableFuture

class MeiliIndexBootstrap(
    private val client: Client,
    private val indexName: String,
    private val reindexer: SubscriptionSearchReindexer
) {

    private val logger = LoggerFactory.getLogger(MeiliIndexBootstrap::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        CompletableFuture.runAsync {
            try {
                configureIndexSettings()
                val count = reindexer.reindexAll()
                logger.info("Bootstrap del indice '$indexName' completado: $count documentos indexados")
            } catch (e: Exception) {
                logger.error("Fallo el bootstrap del indice de Meilisearch: ${e.message}", e)
            }
        }
    }

    private fun configureIndexSettings() {
        try {
            client.createIndex(indexName, "id")
        } catch (e: Exception) {
            logger.debug("El indice '$indexName' ya existe o no se pudo crear: ${e.message}")
        }
        val index = client.index(indexName)
        index.updateSearchableAttributesSettings(arrayOf("fullName", "firstName", "lastName", "dni"))
        index.updateFilterableAttributesSettings(arrayOf("serviceStatus", "installationType"))
        index.updateSortableAttributesSettings(arrayOf("id"))
    }
}
