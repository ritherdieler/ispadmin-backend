package com.dscorp.wispadmin.wispadmin.search.api

import com.dscorp.wispadmin.wispadmin.search.api.model.SearchableDocument

interface SearchIndexer {
    fun index(document: SearchableDocument)
    fun indexAll(documents: List<SearchableDocument>)
    fun delete(id: String)
    fun purge()
}
