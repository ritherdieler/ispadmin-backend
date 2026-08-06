package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.TaskExecutionStatus

data class MonthlyBillingCloseStepResultDto(
    val step: String,
    val status: TaskExecutionStatus,
    val error: String? = null,
    val detail: Map<String, Any?> = emptyMap(),
)

data class MonthlyBillingCloseResultDto(
    val closedMonth: String,
    val overallStatus: TaskExecutionStatus,
    val subscriptionSnapshot: MonthlyBillingCloseStepResultDto,
    val collectsSnapshot: MonthlyBillingCloseStepResultDto,
    val massBilling: MonthlyBillingCloseStepResultDto,
    val message: String,
    val errorMessage: String? = null,
)
