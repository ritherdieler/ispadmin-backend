package com.dscorp.wispadmin.wispadmin.search.application

import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.search.api.SearchIndexer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SubscriptionSearchReindexer(
    private val searchIndexer: SearchIndexer,
    private val subscriptionRepository: SubscriptionRepository,
    private val documentMapper: SubscriptionDocumentMapper
) {

    private val logger = LoggerFactory.getLogger(SubscriptionSearchReindexer::class.java)

    fun reindexAll(): Int {
        return try {
            val documents = subscriptionRepository.findAll().map { documentMapper.toDocument(it) }
            searchIndexer.indexAll(documents)
            documents.size
        } catch (e: Exception) {
            logger.error("Fallo la reindexacion total de suscripciones: ${e.message}", e)
            0
        }
    }
}
