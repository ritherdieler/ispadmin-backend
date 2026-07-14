package com.dscorp.wispadmin.wispadmin.search.controller

import com.dscorp.wispadmin.wispadmin.dto.PageResponseDto
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionDto
import com.dscorp.wispadmin.wispadmin.search.application.SubscriptionSearchReindexer
import com.dscorp.wispadmin.wispadmin.search.application.SubscriptionSearchService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@CrossOrigin(origins = ["*"], maxAge = 3600)
@RestController
@RequestMapping("/subscription")
class SubscriptionSearchController(
    private val subscriptionSearchService: SubscriptionSearchService,
    private val reindexer: SubscriptionSearchReindexer
) {

    @GetMapping("/search")
    fun search(
        @RequestParam(value = "q", required = false, defaultValue = "") q: String,
        @RequestParam(value = "status", required = false) status: String?,
        @RequestParam(value = "page", required = false, defaultValue = "0") page: Int,
        @RequestParam(value = "size", required = false, defaultValue = "20") size: Int
    ): ResponseEntity<PageResponseDto<SubscriptionDto>> {
        return ResponseEntity.ok(subscriptionSearchService.search(q, status, page, size))
    }

    @PostMapping("/search/reindex")
    fun reindex(): ResponseEntity<Map<String, Any>> {
        val count = reindexer.reindexAll()
        return ResponseEntity.ok(mapOf("reindexed" to count))
    }
}
