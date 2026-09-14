package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.SubscriptionActionType
import com.dscorp.wispadmin.wispadmin.service.EconomicResume
import java.util.*

data class DashBoardDto(
    val economicResume: EconomicResume,
    val activeSubscriptions: Long,
    val subscriptionsResume: Map<String, Int>,
    val reconnections:Int,
    val cancellationsResume: CancellationResumeDto,
    val paymentResume: Map<String, Double>,
    val subscriptionsHistoryStatics: List<MonthlySubscriptionResumeDto>,
    val monthlyCollects: List<MonthlyCollectsResumeDto>,
    val grossRevenueHistoryStatics: List<Map<String, Any>>,
    
    // Nuevos campos para datos adicionales
    val assistanceTicketResume: AssistanceTicketResumeDto? = null,
    val planAnalysisResume: PlanAnalysisResumeDto? = null,
    val clientQualityResume: ClientQualityResumeDto? = null,
    val fixedCostAnalysisResume: FixedCostAnalysisResumeDto? = null,
    val installationOrdersResume: InstallationOrdersResumeDto? = null,
    val geographicPerformanceResume: GeographicPerformanceResumeDto? = null,
    val teamPerformanceResume: TeamPerformanceResumeDto? = null,
    val networkHealthResume: NetworkHealthResumeDto? = null,
    val clientLifecycleResume: ClientLifecycleResumeDto? = null,
    val subscriptionLogSummary: Map<SubscriptionActionType, ActionTypeSummary> = emptyMap()
)

data class PaymentResume(
    val methodStatics: Map<String, Double>,
)

// Nuevas clases DTO
data class AssistanceTicketResumeDto(
    val totalTickets: Int,
    val pendingTickets: Int,
    val assignedTickets: Int,
    val inProgressTickets: Int,
    val resolvedTickets: Int,
    val reopenedTickets: Int,
    val closedTickets: Int,
    val cancelledTickets: Int,
    val averageResolutionTimeHours: Double,
    val reopenRate: Double,
    val ticketsByCategory: Map<String, Int>,
    val ticketsByPriority: List<Int>
)

data class PlanAnalysisResumeDto(
    val planDistribution: Map<String, Int>,
    val plansBySpeed: Map<String, Int>,
    val plansByType: Map<String, Int>,
    val mostPopularPlan: String,
    val averageDownloadSpeed: Double,
    val averageRevenuePerPlan: Map<String, Double>
)

data class ClientQualityResumeDto(
    val clientsByQualification: List<Int>,
    val averageAntiquityMonths: Double,
    val paymentCommitmentPercentage: Double,
    val paymentMethodDistribution: Map<String, Int>,
    val averageDebtPerClient: Double,
    val clientSatisfactionScore: Double
)

data class FixedCostAnalysisResumeDto(
    val costsByCategory: Map<String, Double>,
    val costTrend: List<CostTrendDto>,
    val costToIncomeRatio: Double,
    val largestExpenseCategory: String,
    val largestExpenseAmount: Double
)

data class CostTrendDto(
    val date: Date,
    val amount: Double,
    val category: String
)

data class InstallationOrdersResumeDto(
    val pendingOrders: Int,
    val completedOrders: Int,
    val cancelledOrders: Int,
    val averageFulfillmentDays: Double,
    val ordersByTechnician: Map<String, Int>,
    val ordersByPlace: Map<String, Int>,
    val conversionRate: Double,
    val installationsByMonth: List<InstallationMonthlyDto>
)

data class InstallationMonthlyDto(
    val date: Date,
    val count: Int
)

data class GeographicPerformanceResumeDto(
    val clientDensityByPlace: Map<String, Int>,
    val revenueByPlace: Map<String, Double>,
    val incidenceRateByPlace: Map<String, Double>,
    val growthRateByPlace: Map<String, Double>
)

data class TeamPerformanceResumeDto(
    val installationsByTechnician: Map<String, Int>,
    val salesByUser: Map<String, Int>,
    val ticketsByTechnician: Map<String, Int>,
    val averageResolutionTimeByTechnician: Map<String, Double>,
    val technicianEfficiencyScore: Map<String, Double>
)

data class NetworkHealthResumeDto(
    val devicesByType: Map<String, Int>,
    val devicesByStatus: Map<String, Int>,
    val nodesLoad: Map<String, Double>,
    val failureRateByDeviceModel: Map<String, Double>,
    val overloadedNodes: List<String>
)

data class ClientLifecycleResumeDto(
    val averageDaysToFirstIssue: Double,
    val averageDaysToCancellation: Double,
    val cancellationReasons: Map<String, Int>,
    val cancellationsByPlan: Map<String, Int>,
    val cancellationsByZone: Map<String, Int>,
    val seasonalCancellationRate: Map<String, Double>
)
