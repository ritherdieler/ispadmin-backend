package com.dscorp.wispadmin.wispadmin.search.application

import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.search.api.SearchIndexer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class SubscriptionIndexingListener(
    private val searchIndexer: SearchIndexer,
    private val subscriptionRepository: SubscriptionRepository,
    private val documentMapper: SubscriptionDocumentMapper
) {

    private val logger = LoggerFactory.getLogger(SubscriptionIndexingListener::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onSubscriptionChanged(event: SubscriptionChangedEvent) {
        try {
            val subscription = subscriptionRepository.findById(event.subscriptionId).orElse(null) ?: return
            searchIndexer.index(documentMapper.toDocument(subscription))
        } catch (e: Exception) {
            logger.error("No se pudo indexar la suscripcion ${event.subscriptionId}: ${e.message}", e)
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onSubscriptionDeleted(event: SubscriptionDeletedEvent) {
        try {
            searchIndexer.delete(event.subscriptionId.toString())
        } catch (e: Exception) {
            logger.error("No se pudo eliminar del indice la suscripcion ${event.subscriptionId}: ${e.message}", e)
        }
    }
}
