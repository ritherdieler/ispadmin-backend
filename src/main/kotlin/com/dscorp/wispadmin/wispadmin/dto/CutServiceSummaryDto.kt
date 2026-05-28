package com.dscorp.wispadmin.wispadmin.dto

data class CutServiceSummaryDto(
    val debtors: CutServiceResultDto,
    val cancelled: CutServiceResultDto,
    val totalProcessed: Int,
    val totalCreated: Int,
    val totalAlreadyExists: Int,
    val totalErrors: Int,
    val totalOmittedByTvCable: Int,
    val message: String,
    val allFailedItems: List<String> = emptyList(),
    val allNotProcessedItems: List<String> = emptyList(),
    val allAlreadyExistingItems: List<String> = emptyList()
)

