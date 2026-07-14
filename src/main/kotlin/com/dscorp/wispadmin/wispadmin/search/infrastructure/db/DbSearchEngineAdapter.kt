package com.dscorp.wispadmin.wispadmin.search.infrastructure.db

import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.search.api.SearchEngine
import com.dscorp.wispadmin.wispadmin.search.api.model.SearchHit
import com.dscorp.wispadmin.wispadmin.search.api.model.SearchPage
import com.dscorp.wispadmin.wispadmin.search.api.model.SearchQuery
import org.springframework.stereotype.Component
import kotlin.math.ceil

@Component("dbSearchEngineAdapter")
class DbSearchEngineAdapter(
    private val subscriptionRepository: SubscriptionRepository
) : SearchEngine {

    override fun search(query: SearchQuery): SearchPage<SearchHit> {
        val matches = subscriptionRepository.searchByNameOrLastName(query.term.trim())

        val statusFilter = query.filters["serviceStatus"]
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { ServiceStatus.valueOf(it.trim().uppercase()) }.getOrNull() }

        val filtered: List<Subscription> = if (statusFilter != null) {
            matches.filter { it.serviceStatus == statusFilter }
        } else {
            matches
        }

        val total = filtered.size.toLong()
        val size = if (query.size <= 0) 20 else query.size
        val from = query.page * size
        val pageItems = if (from >= filtered.size) {
            emptyList()
        } else {
            filtered.subList(from, minOf(from + size, filtered.size))
        }

        val hits = pageItems.map { SearchHit(id = it.id?.toString().orEmpty()) }
        val totalPages = if (total == 0L) 0 else ceil(total.toDouble() / size).toInt()

        return SearchPage(
            items = hits,
            page = query.page,
            size = size,
            total = total,
            totalPages = totalPages
        )
    }
}
