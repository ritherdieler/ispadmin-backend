package com.dscorp.wispadmin.wispadmin.search.infrastructure.meili

import com.dscorp.wispadmin.wispadmin.search.api.SearchEngine
import com.dscorp.wispadmin.wispadmin.search.api.model.SearchHit
import com.dscorp.wispadmin.wispadmin.search.api.model.SearchPage
import com.dscorp.wispadmin.wispadmin.search.api.model.SearchQuery
import com.meilisearch.sdk.Client
import com.meilisearch.sdk.SearchRequest
import com.meilisearch.sdk.model.SearchResult
import kotlin.math.ceil

class MeiliSearchEngineAdapter(
    private val client: Client,
    private val indexName: String
) : SearchEngine {

    override fun search(query: SearchQuery): SearchPage<SearchHit> {
        val size = if (query.size <= 0) 20 else query.size
        val offset = query.page * size

        val request = SearchRequest(query.term)
            .setOffset(offset)
            .setLimit(size)

        buildFilter(query.filters)?.let { request.setFilter(it) }

        val result = client.index(indexName).search(request) as SearchResult
        val hits = result.hits.map { hit ->
            val id = hit["id"]?.let { normalizeId(it) } ?: ""
            SearchHit(id = id, fields = hit)
        }

        val total = result.estimatedTotalHits.toLong()
        val totalPages = if (total == 0L) 0 else ceil(total.toDouble() / size).toInt()

        return SearchPage(
            items = hits,
            page = query.page,
            size = size,
            total = total,
            totalPages = totalPages
        )
    }

    private fun buildFilter(filters: Map<String, String>): Array<String>? {
        val expressions = filters
            .filterValues { it.isNotBlank() }
            .map { (field, value) -> "$field = \"${value.trim()}\"" }
        return if (expressions.isEmpty()) null else expressions.toTypedArray()
    }

    private fun normalizeId(raw: Any): String {
        return when (raw) {
            is Number -> raw.toLong().toString()
            else -> raw.toString()
        }
    }
}
