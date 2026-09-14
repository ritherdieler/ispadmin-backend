package com.dscorp.wispadmin.wispadmin.dto

data class PageResponseDto<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val total: Long,
    val totalPages: Int
)
