package com.dscorp.wispadmin.wispadmin.search.infrastructure.meili

import com.dscorp.wispadmin.wispadmin.search.api.SearchIndexer
import com.dscorp.wispadmin.wispadmin.search.api.model.SearchableDocument
import com.google.gson.Gson
import com.meilisearch.sdk.Client
import org.slf4j.LoggerFactory

class MeiliSearchIndexerAdapter(
    private val client: Client,
    private val indexName: String,
    private val primaryKey: String = "id"
) : SearchIndexer {

    private val logger = LoggerFactory.getLogger(MeiliSearchIndexerAdapter::class.java)
    private val gson = Gson()

    override fun index(document: SearchableDocument) {
        try {
            val json = gson.toJson(listOf(document.fields))
            client.index(indexName).addDocuments(json, primaryKey)
        } catch (e: Exception) {
            logger.error("Error al indexar documento ${document.id} en Meilisearch: ${e.message}", e)
        }
    }

    override fun indexAll(documents: List<SearchableDocument>) {
        if (documents.isEmpty()) return
        try {
            val json = gson.toJson(documents.map { it.fields })
            client.index(indexName).addDocuments(json, primaryKey)
        } catch (e: Exception) {
            logger.error("Error al indexar lote de ${documents.size} documentos en Meilisearch: ${e.message}", e)
        }
    }

    override fun delete(id: String) {
        try {
            client.index(indexName).deleteDocument(id)
        } catch (e: Exception) {
            logger.error("Error al eliminar documento $id de Meilisearch: ${e.message}", e)
        }
    }

    override fun purge() {
        try {
            client.index(indexName).deleteAllDocuments()
        } catch (e: Exception) {
            logger.error("Error al purgar el indice de Meilisearch: ${e.message}", e)
        }
    }
}
