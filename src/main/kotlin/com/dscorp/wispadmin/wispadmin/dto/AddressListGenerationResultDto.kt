package com.dscorp.wispadmin.wispadmin.dto

data class AddressListGenerationResultDto(
    val processedCount: Int,
    val createdCount: Int,
    val alreadyExistsCount: Int,
    val deletedCount: Int,
    val errorCount: Int,
    val totalNotCreated: Int,
    val message: String,
    val createdSubscriptions: List<String>,
    val alreadyExistsSubscriptions: List<String>,
    val notProcessedSubscriptions: List<String>,
    val failedSubscriptions: List<String>
)
