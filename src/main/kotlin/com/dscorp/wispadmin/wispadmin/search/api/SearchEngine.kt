package com.dscorp.wispadmin.wispadmin.search.api

import com.dscorp.wispadmin.wispadmin.search.api.model.SearchHit
import com.dscorp.wispadmin.wispadmin.search.api.model.SearchPage
import com.dscorp.wispadmin.wispadmin.search.api.model.SearchQuery

interface SearchEngine {
    fun search(query: SearchQuery): SearchPage<SearchHit>
}
