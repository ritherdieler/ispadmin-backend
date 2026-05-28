package com.dscorp.wispadmin.wispadmin.dto

data class CutServiceResultDto(
    val processedCount: Int,
    val createdCount: Int,
    val deletedCount: Int,
    val alreadyExistsCount: Int,
    val errorCount: Int,
    val message: String,
    val debtorsCount: Int,
    val cancelledCount: Int,
    val omittedByTvCable: Int,
    val failedItems: List<String> = emptyList(),
    val notProcessedItems: List<String> = emptyList(),
    val alreadyExistingItems: List<String> = emptyList()
)

