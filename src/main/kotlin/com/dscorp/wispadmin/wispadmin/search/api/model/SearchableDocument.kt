package com.dscorp.wispadmin.wispadmin.search.api.model

data class SearchableDocument(
    val id: String,
    val fields: Map<String, Any?>
)
