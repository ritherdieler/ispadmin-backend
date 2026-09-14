package com.dscorp.wispadmin.wispadmin.search.api.model

data class SearchQuery(
    val term: String,
    val filters: Map<String, String> = emptyMap(),
    val page: Int = 0,
    val size: Int = 20
)
