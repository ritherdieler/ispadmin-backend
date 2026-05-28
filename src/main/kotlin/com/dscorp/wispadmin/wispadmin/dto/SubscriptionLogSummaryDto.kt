package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.SubscriptionActionType
import java.time.YearMonth

data class SubscriptionLogSummaryDto(
    val actionType: SubscriptionActionType,
    val count: Long,
    val period: YearMonth
)

data class MonthlyDetailDto(
    val count: Long,
    val period: YearMonth
)

data class ActionTypeSummary(
    val actionType: SubscriptionActionType,
    val totalCount: Long,
    val monthlyDetails: List<MonthlyDetailDto>
)

data class SubscriptionLogSummaryResponse(
    val summary: Map<SubscriptionActionType, ActionTypeSummary>,
    val totalOperations: Long
) 