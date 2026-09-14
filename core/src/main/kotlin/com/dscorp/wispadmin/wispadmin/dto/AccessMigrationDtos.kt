package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.AccessMigrationStage

data class AccessMigrationProgressDto(
    val subscriptionId: Int,
    val stage: AccessMigrationStage,
    val attempt: Int,
    val pppoeUsername: String? = null,
    val failureReason: String? = null,
    val quarantineUntil: String? = null,
    val done: Boolean = stage.isClientComplete(),
    val message: String? = stage.toClientMessage(),
)

data class AccessMigrationEligibleDto(
    val subscriptionId: Int,
    val name: String,
    val ip: String?,
    val onuSn: String?,
    val productClass: String? = null,
    val planName: String? = null,
    val reason: String? = null,
    val eligible: Boolean,
)

data class AccessMigrationEligiblePageDto(
    val items: List<AccessMigrationEligibleDto>,
)