package com.dscorp.wispadmin.wispadmin.search.infrastructure

import com.dscorp.wispadmin.wispadmin.search.api.SearchIndexer
import com.dscorp.wispadmin.wispadmin.search.api.model.SearchableDocument
import org.springframework.stereotype.Component

@Component
class NoOpSearchIndexer : SearchIndexer {
    override fun index(document: SearchableDocument) {}
    override fun indexAll(documents: List<SearchableDocument>) {}
    override fun delete(id: String) {}
    override fun purge() {}
}
