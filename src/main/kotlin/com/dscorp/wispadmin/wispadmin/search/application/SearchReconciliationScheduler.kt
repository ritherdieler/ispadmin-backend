package com.dscorp.wispadmin.wispadmin.search.application

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "search", name = ["provider"], havingValue = "meilisearch", matchIfMissing = true)
class SearchReconciliationScheduler(
    private val reindexer: SubscriptionSearchReindexer
) {

    private val logger = LoggerFactory.getLogger(SearchReconciliationScheduler::class.java)

    @Scheduled(fixedDelayString = "\${search.reconciliation.interval-ms:1800000}", initialDelayString = "\${search.reconciliation.initial-delay-ms:1800000}")
    fun reconcile() {
        val count = reindexer.reindexAll()
        logger.info("Reconciliacion de indice de busqueda completada: $count documentos")
    }
}
