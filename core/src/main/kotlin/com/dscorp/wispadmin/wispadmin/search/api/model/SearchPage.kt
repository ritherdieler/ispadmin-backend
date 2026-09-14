package com.dscorp.wispadmin.wispadmin.search.api.model

data class SearchHit(
    val id: String,
    val fields: Map<String, Any?> = emptyMap()
)

data class SearchPage<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val total: Long,
    val totalPages: Int
)
