package com.dscorp.wispadmin.wispadmin.search.application

import com.dscorp.wispadmin.wispadmin.dto.PageResponseDto
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionDto
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.search.api.SearchEngine
import com.dscorp.wispadmin.wispadmin.search.api.model.SearchHit
import com.dscorp.wispadmin.wispadmin.search.api.model.SearchPage
import com.dscorp.wispadmin.wispadmin.search.api.model.SearchQuery
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SubscriptionSearchService(
    private val searchEngine: SearchEngine,
    @Qualifier("dbSearchEngineAdapter") private val fallbackSearchEngine: SearchEngine,
    private val subscriptionRepository: SubscriptionRepository,
    private val paymentRepository: PaymentRepository,
) {

    private val logger = LoggerFactory.getLogger(SubscriptionSearchService::class.java)

    @Transactional(readOnly = true)
    fun search(term: String?, status: String?, page: Int, size: Int): PageResponseDto<SubscriptionDto> {
        val filters = buildFilters(status)
        val query = SearchQuery(term = term.orEmpty().trim(), filters = filters, page = page, size = size)

        val searchPage = runSearch(query)
        val items = hydrate(searchPage.items)

        return PageResponseDto(
            items = items,
            page = searchPage.page,
            size = searchPage.size,
            total = searchPage.total,
            totalPages = searchPage.totalPages
        )
    }

    private fun runSearch(query: SearchQuery): SearchPage<SearchHit> {
        return try {
            searchEngine.search(query)
        } catch (e: Exception) {
            logger.error("Fallo el motor de busqueda principal, usando fallback de BD: ${e.message}", e)
            if (searchEngine === fallbackSearchEngine) {
                SearchPage(emptyList(), query.page, query.size, 0, 0)
            } else {
                fallbackSearchEngine.search(query)
            }
        }
    }

    private fun hydrate(hits: List<SearchHit>): List<SubscriptionDto> {
        if (hits.isEmpty()) return emptyList()
        val orderedIds = hits.mapNotNull { it.id.toIntOrNull() }
        if (orderedIds.isEmpty()) return emptyList()

        val subscriptions = subscriptionRepository.findAllById(orderedIds)
        if (subscriptions.isNotEmpty()) {
            val paymentsBySubscriptionId = paymentRepository
                .findBySubscriptionIdInFetchResponsible(orderedIds)
                .groupBy { it.subscription?.id }
            subscriptions.forEach { subscription ->
                subscription.payments = paymentsBySubscriptionId[subscription.id].orEmpty().toMutableSet()
            }
        }
        val byId = subscriptions.associateBy { it.id }
        return orderedIds.mapNotNull { byId[it]?.toDto() }
    }

    private fun buildFilters(status: String?): Map<String, String> {
        val cleaned = status?.trim().orEmpty()
        return if (cleaned.isEmpty()) emptyMap() else mapOf("serviceStatus" to cleaned)
    }
}
