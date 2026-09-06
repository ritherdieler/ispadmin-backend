package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.*
import com.dscorp.wispadmin.wispadmin.data.model.toDto
import com.dscorp.wispadmin.wispadmin.dto.*
import com.dscorp.wispadmin.wispadmin.repository.*
import com.dscorp.wispadmin.wispadmin.oltclient.OltGatewayHttpClient
import com.dscorp.wispadmin.wispadmin.util.PerformanceMonitor
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.YearMonth
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.time.temporal.ChronoUnit
const val CABLE_TV = "cable"

@Service
class DashBoardService(
    private val paymentRepository: PaymentRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val subscriptionsStaticsRepository: SubscriptionsStaticsRepository,
    private val monthlyCollectsRepository: MonthlyCollectsRepository,
    private val outLayRepository: OutlayRepository,
    private val fixedCostRepository: FixedCostRepository,
    private val corporateClientRepository: CorporationCustomerRepository,
    private val subscriptionLogRepository: SubscriptionLogRepository,
    private val assistanceTicketRepository: AssistanceTicketRepository,
    private val planRepository: PlanRepository,
    private val installationOrderRepository: InstallationOrderRepository,
    private val placeRepository: PlaceRepository,
    private val userRepository: UserRepository,
    private val networkDeviceRepository: NetworkDeviceRepository,
    private val napBoxRepository: NapBoxRepository,
    private val oltGatewayHttp: ObjectProvider<OltGatewayHttpClient>,
    private val objectMapper: ObjectMapper,
    private val performanceMonitor: PerformanceMonitor,
    private val paymentStatisticsService: PaymentStatisticsService
) {

    private val dashboardZone: ZoneId = ZoneId.of("America/Lima")

    private fun currentMonthRange(): Pair<LocalDateTime, LocalDateTime> {
        val currentMonth = YearMonth.now(dashboardZone)
        return currentMonth.atDay(1).atStartOfDay() to currentMonth.plusMonths(1).atDay(1).atStartOfDay()
    }

    private fun lastCompletedMonthsRange(monthsBack: Long): Pair<LocalDateTime, LocalDateTime> {
        val currentMonth = YearMonth.now(dashboardZone)
        return currentMonth.minusMonths(monthsBack).atDay(1).atStartOfDay() to currentMonth.atDay(1).atStartOfDay()
    }

    private fun LocalDateTime.toLegacyDate(): Date = Date.from(atZone(dashboardZone).toInstant())

    fun createDashBoard(): DashBoardDto {
        return performanceMonitor.measureTime("createDashBoard") {
            // Preparar fechas una sola vez
            val (paymentStatsStart, paymentStatsEnd) = lastCompletedMonthsRange(monthsBack = 8)
            val (currentMonthStart, nextMonthStart) = currentMonthRange()
            val currentMonthEndInclusive = nextMonthStart.minusNanos(1)
            val billingCycleStart = currentMonthStart.minusDays(1)
            val billingCycleEnd = nextMonthStart.minusDays(1)
            val firstDayOfMonthDate = currentMonthStart.toLegacyDate()
            val lastDayOfMonthDate = currentMonthEndInclusive.toLegacyDate()

            // ===== GRUPO 1: Consultas de pagos (independientes entre sí) =====
            val grossRevenueFuture = CompletableFuture.supplyAsync {
                performanceMonitor.measureTime("paymentRepository.getGrossRevenueBetween") {
                    paymentRepository.getGrossRevenueBetween(billingCycleStart, billingCycleEnd)
                }
            }
            
            val totalRaisedFuture = CompletableFuture.supplyAsync {
                performanceMonitor.measureTime("paymentRepository.getTotalRaisedBetween") {
                    paymentRepository.getTotalRaisedBetween(billingCycleStart, billingCycleEnd)
                }
            }
            
            val totalDiscountFuture = CompletableFuture.supplyAsync {
                performanceMonitor.measureTime("paymentRepository.getTotalDiscountsBetween") {
                    paymentRepository.getTotalDiscountsBetween(billingCycleStart, billingCycleEnd)
                }
            }
            
            val totalToCollectFuture = CompletableFuture.supplyAsync {
                performanceMonitor.measureTime("paymentRepository.calculateTotalToCollectBetween") {
                    paymentRepository.calculateTotalToCollectBetween(billingCycleStart, billingCycleEnd)
                }
            }

            val grossRevenueHistoryFuture = CompletableFuture.supplyAsync {
                performanceMonitor.measureTime("paymentRepository.getTop6GrossRevenueHistory") {
                    paymentRepository.getTop6GrossRevenueHistory()
                }
            }

            val paymentMethodStaticsFuture = CompletableFuture.supplyAsync {
                performanceMonitor.measureTime("paymentStatisticsService.getPaymentMethodStatisticsOptimized") {
                    paymentStatisticsService.getPaymentMethodStatisticsOptimized(paymentStatsStart, paymentStatsEnd)
                }
            }

            // ===== GRUPO 2: Consultas de suscripciones y logs (independientes entre sí) =====
            val canceledSubscriptionsByUsersFuture = CompletableFuture.supplyAsync {
                performanceMonitor.measureTime("subscriptionLogRepository.getCanceledSubscriptionsByUser") {
                    subscriptionLogRepository.getCanceledSubscriptionsByUser(firstDayOfMonthDate, lastDayOfMonthDate)
                }
            }

            val canceledSubscriptionsBySystemFuture = CompletableFuture.supplyAsync {
                performanceMonitor.measureTime("subscriptionLogRepository.getCanceledSubscriptionsBySystem") {
                    subscriptionLogRepository.getCanceledSubscriptionsBySystem(firstDayOfMonthDate, lastDayOfMonthDate)
                }
            }

            val activeSubscriptionsFuture = CompletableFuture.supplyAsync {
                performanceMonitor.measureTime("subscriptionRepository.countByServiceStatus") {
                    subscriptionRepository.countByServiceStatus(ServiceStatus.ACTIVE)
                }
            }

            val reconnectionsFuture = CompletableFuture.supplyAsync {
                performanceMonitor.measureTime("subscriptionLogRepository.getReconnections") {
                    subscriptionLogRepository.getReconnections(firstDayOfMonthDate, lastDayOfMonthDate)
                }
            }
            
            val subscriptionLogSummaryFuture = CompletableFuture.supplyAsync {
                performanceMonitor.measureTime("getSubscriptionLogSummary") {
                    getSubscriptionLogSummary()
                }
            }

            // ===== GRUPO 3: Consultas de datos corporativos y estadísticas (independientes entre sí) =====
            val grossCorporateRevenueFuture = CompletableFuture.supplyAsync {
                performanceMonitor.measureTime("corporateClientRepository.sumActiveInvoicedAmount") {
                    corporateClientRepository.sumActiveInvoicedAmount()
                }
            }

            val installationsFuture = CompletableFuture.supplyAsync {
                performanceMonitor.measureTime("getInstallationResume") {
                    getInstallationResume(currentMonthStart, currentMonthEndInclusive).toMutableMap()
                }
            }

            val subscriptionsHistoryStaticsFuture = CompletableFuture.supplyAsync {
                performanceMonitor.measureTime("subscriptionsStaticsRepository.findTop10ByOrderByDateDesc") {
                    subscriptionsStaticsRepository.findTop10ByOrderByDateDesc().map { it.toDto() }
                        .sortedBy { it.date }
                }
            }

            val monthlyCollectsFuture = CompletableFuture.supplyAsync {
                performanceMonitor.measureTime("monthlyCollectsRepository.findTop8ByOrderByDateDesc") {
                    monthlyCollectsRepository.findTop8ByOrderByDateDesc().reversed().map { it.toDto() }
                }
            }

            // ===== GRUPO 4: Consultas de costos (independientes entre sí) =====
            val outLaysFromCurrentMonthFuture = CompletableFuture.supplyAsync {
                performanceMonitor.measureTime("outLayRepository.sumAmountByDateBetween") {
                    outLayRepository.sumAmountByDateBetween(firstDayOfMonthDate, lastDayOfMonthDate)
                }
            }

            val fixedCostAmountFuture = CompletableFuture.supplyAsync {
                performanceMonitor.measureTime("fixedCostRepository.sumAllAmounts") {
                    fixedCostRepository.sumAllAmounts()
                }
            }

            // Esperar a que todas las consultas terminen y obtener resultados
            val grossRevenue = grossRevenueFuture.get()
            val totalRaised = totalRaisedFuture.get()
            val totalDiscount = totalDiscountFuture.get()
            val totalToCollect = totalToCollectFuture.get()
            val grossRevenueHistory = grossRevenueHistoryFuture.get()
            val paymentMethodStaticsByDate = paymentMethodStaticsFuture.get()

            val canceledSubscriptionsByUsers = canceledSubscriptionsByUsersFuture.get()
            val canceledSubscriptionsBySystem = canceledSubscriptionsBySystemFuture.get()
            val activeSubscriptions = activeSubscriptionsFuture.get()
            val reconnections = reconnectionsFuture.get()
            val subscriptionLogSummary = subscriptionLogSummaryFuture.get()

            val grossCorporateRevenue = grossCorporateRevenueFuture.get()
            val installations = installationsFuture.get()
            val subscriptionsHistoryStatics = subscriptionsHistoryStaticsFuture.get()
            val monthlyCollects = monthlyCollectsFuture.get()

            val outLaysFromCurrentMonth = outLaysFromCurrentMonthFuture.get()
            val fixedCostAmount = fixedCostAmountFuture.get()

            // Construir el resultado final
            val economicResume = EconomicResume(
                grossRevenue = grossRevenue,
                totalRaised = totalRaised,
                totalDiscount = totalDiscount,
                totalToCollect = totalToCollect,
                outLaysFromCurrentMonth = outLaysFromCurrentMonth,
                fixedCosts = fixedCostAmount,
                margin = grossRevenue - fixedCostAmount - outLaysFromCurrentMonth,
                freeCash = totalRaised - outLaysFromCurrentMonth - fixedCostAmount,
                corporateGrossRevenue = grossCorporateRevenue
            )
            
            val result = DashBoardDto(
                economicResume = economicResume,
                subscriptionsResume = installations,
                reconnections = reconnections.size,
                activeSubscriptions = activeSubscriptions,
                cancellationsResume = CancellationResumeDto(
                    cancelledByUsers = canceledSubscriptionsByUsers.size,
                    cancelledBySystem = canceledSubscriptionsBySystem.size
                ),
                paymentResume = paymentMethodStaticsByDate,
                subscriptionsHistoryStatics = subscriptionsHistoryStatics,
                monthlyCollects = monthlyCollects,
                grossRevenueHistoryStatics = grossRevenueHistory,
                subscriptionLogSummary = subscriptionLogSummary
            )
            
            // Imprimir resumen de rendimiento
            performanceMonitor.printPerformanceSummary()
            
            result
        }
    }


    fun createDashBoardV2(): DashBoardDto {
        val (paymentStatsStart, paymentStatsEnd) = lastCompletedMonthsRange(monthsBack = 8)
        val (currentMonthStart, nextMonthStart) = currentMonthRange()
        val currentMonthEndInclusive = nextMonthStart.minusNanos(1)
        val billingCycleStart = currentMonthStart.minusDays(1)
        val billingCycleEnd = nextMonthStart.minusDays(1)
        val grossRevenue = paymentRepository.getGrossRevenueBetween(billingCycleStart, billingCycleEnd)
        val totalRaised = paymentRepository.getTotalRaisedBetween(billingCycleStart, billingCycleEnd)
        val totalDiscount = paymentRepository.getTotalDiscountsBetween(billingCycleStart, billingCycleEnd)
        val totalToCollect = paymentRepository.calculateTotalToCollectBetween(billingCycleStart, billingCycleEnd)

        val grossRevenueHistory = paymentRepository.getTop6GrossRevenueHistory()

        val paymentMethodStatics =
            paymentRepository.getLasMonthsPaymentMethodStatics(paymentStatsStart, paymentStatsEnd)
                .sortedBy { it.billingDateDatetime }
                .groupBy {
                    it.billingDateDatetime.month.value.getMonthName()
                        .substring(0, 3).capitalize() + "."
                }

        val paymentMethodStaticsByDate: Map<String, Double> = paymentMethodStatics.mapValues {
            ((it.value.filter {
                it.paid && (it.method.equals("Plin") || it.method.equals("Yape") || it.method.equals(
                    "Transferencia"
                ))
            }.size * 100) / it.value.size).toDouble()
        }

        val firstDayOfMonthDate = currentMonthStart.toLegacyDate()
        val lastDayOfMonthDate = currentMonthEndInclusive.toLegacyDate()

        val canceledSubscriptionsByUsers = subscriptionRepository.findQuantityByCancellationDate(
            currentMonthStart,
            currentMonthEndInclusive
        )

        val canceledSubscriptionsBySystem = subscriptionLogRepository.getCanceledSubscriptionsBySystem(
            firstDayOfMonthDate,
            lastDayOfMonthDate
        )

//get the gross revenue
        val grossCorporateRevenue = corporateClientRepository.sumActiveInvoicedAmount()

        val installations = getInstallationResume(currentMonthStart, currentMonthEndInclusive).toMutableMap()

        val subscriptionsHistoryStatics = subscriptionsStaticsRepository.findTop10ByOrderByDateDesc().map { it.toDto() }
            .sortedBy { it.date }

        val monthlyCollects =
            monthlyCollectsRepository.findTop8ByOrderByDateDesc().reversed().map { it.toDtoAsCurrency() }

        val outLaysFromCurrentMonth = outLayRepository.sumAmountByDateBetween(firstDayOfMonthDate, lastDayOfMonthDate)

        val fixedCostAmount = fixedCostRepository.sumAllAmounts()

        val economicResume = EconomicResume(
            grossRevenue = grossRevenue,
            totalRaised = totalRaised,
            totalDiscount = totalDiscount,
            totalToCollect = totalToCollect,
            outLaysFromCurrentMonth = outLaysFromCurrentMonth,
            fixedCosts = fixedCostAmount,
            margin = grossRevenue - fixedCostAmount - outLaysFromCurrentMonth,
            freeCash = totalRaised - outLaysFromCurrentMonth - fixedCostAmount,
            corporateGrossRevenue = grossCorporateRevenue
        )

        val activeSubscriptions = subscriptionRepository.countByServiceStatus(ServiceStatus.ACTIVE)
        val reconnections = subscriptionLogRepository.getReconnections(firstDayOfMonthDate, lastDayOfMonthDate)

        return DashBoardDto(
            economicResume = economicResume,
            subscriptionsResume = installations,
            activeSubscriptions = activeSubscriptions,
            cancellationsResume = CancellationResumeDto(
                cancelledByUsers = canceledSubscriptionsByUsers,
                cancelledBySystem = canceledSubscriptionsBySystem.size
            ),
            reconnections = reconnections.size,
            paymentResume = paymentMethodStaticsByDate,
            subscriptionsHistoryStatics = subscriptionsHistoryStatics,
            grossRevenueHistoryStatics = grossRevenueHistory,
            monthlyCollects = monthlyCollects,
            subscriptionLogSummary = getSubscriptionLogSummary()
        )
    }

    private fun getSubscriptionLogSummary(): Map<SubscriptionActionType, ActionTypeSummary> {
        val startDate = Calendar.getInstance().apply {
            add(Calendar.MONTH, -12)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val summary = subscriptionLogRepository.getSubscriptionLogSummary(startDate)
            .map {
                val year = (it["year"] as Number).toInt()
                val month = (it["month"] as Number).toInt()
                SubscriptionLogSummaryDto(
                    actionType = it["actionType"] as SubscriptionActionType,
                    count = (it["count"] as Number).toLong(),
                    period = YearMonth.of(year, month)
                )
            }

        // Agrupar por tipo de acción
        val groupedSummary = summary
            .groupBy { it.actionType }
            .mapValues { (actionType, logs) ->
                ActionTypeSummary(
                    actionType = actionType,
                    totalCount = logs.sumOf { it.count },
                    monthlyDetails = logs
                        .sortedWith(
                            compareByDescending<SubscriptionLogSummaryDto> { it.period }
                                .thenByDescending { it.count }
                        )
                        .map { MonthlyDetailDto(count = it.count, period = it.period) }
                )
            }

        // Crear el tipo combinado TOTAL_CANCEL_SUBSCRIPTION
        val cancelSubscriptionData = groupedSummary[SubscriptionActionType.CANCEL_SUBSCRIPTION]
        val canceledByStoredProcedureData = groupedSummary[SubscriptionActionType.CANCELED_BY_STORED_PROCEDURE]
        
        val totalCancelSubscription = if (cancelSubscriptionData != null || canceledByStoredProcedureData != null) {
            val combinedMonthlyDetails = mutableMapOf<YearMonth, Long>()
            
            // Combinar datos mensuales de ambos tipos
            cancelSubscriptionData?.monthlyDetails?.forEach { detail ->
                combinedMonthlyDetails[detail.period] = (combinedMonthlyDetails[detail.period] ?: 0L) + detail.count
            }
            
            canceledByStoredProcedureData?.monthlyDetails?.forEach { detail ->
                combinedMonthlyDetails[detail.period] = (combinedMonthlyDetails[detail.period] ?: 0L) + detail.count
            }
            
            val totalCount = (cancelSubscriptionData?.totalCount ?: 0L) + (canceledByStoredProcedureData?.totalCount ?: 0L)
            val monthlyDetails = combinedMonthlyDetails
                .map { MonthlyDetailDto(count = it.value, period = it.key) }
                .sortedWith(
                    compareByDescending<MonthlyDetailDto> { it.period }
                        .thenByDescending { it.count }
                )
            
            ActionTypeSummary(
                actionType = SubscriptionActionType.TOTAL_CANCEL_SUBSCRIPTION,
                totalCount = totalCount,
                monthlyDetails = monthlyDetails
            )
        } else null

        // Construir el mapa final incluyendo el tipo combinado
        val finalMap = groupedSummary.toMutableMap()
        
        if (totalCancelSubscription != null) {
            // Agregar el tipo combinado
            finalMap[SubscriptionActionType.TOTAL_CANCEL_SUBSCRIPTION] = totalCancelSubscription
        }

        return finalMap
            .toList()
            .sortedByDescending { it.second.totalCount }
            .toMap()
    }


    private fun getInstallationResume(
        startDate: LocalDateTime,
        endDateInclusive: LocalDateTime
    ): Map<String, Int> {
        val subscriptions =
            subscriptionRepository.findBySubscriptionDateGreaterThanEqualAndSubscriptionDateLessThanEqual(
                startDate,
                endDateInclusive
            )

        val cableTvInstallations = subscriptions.filter {
            it.isCableTvInstallation()
        }.size

        val internetFiberInstallations =
            subscriptions.filter { !it.isCableTvInstallation() && !it.isWirelessInstallation() }.size
        val wirelessInstallations = subscriptions.filter { it.isWirelessInstallation() }.size

        return mapOf(
            "cableTvInstallations" to cableTvInstallations,
            "fiberInternetInstallations" to internetFiberInstallations,
            "wirelessInternetInstallations" to wirelessInstallations
        )
    }

    /**
     * Obtiene datos detallados de tickets de asistencia
     */
    fun getAssistanceTicketsData(): AssistanceTicketResumeDto {
        val allTickets = assistanceTicketRepository.findAll()

        // Contar tickets por estado
        val pendingTickets = allTickets.count { it.status == AssistanceTicketStatus.PENDING }
        val assignedTickets = allTickets.count { it.status == AssistanceTicketStatus.ASSIGNED }
        val inProgressTickets = allTickets.count { it.status == AssistanceTicketStatus.IN_PROGRESS }
        val resolvedTickets = allTickets.count { it.status == AssistanceTicketStatus.RESOLVED }
        val reopenedTickets = allTickets.count { it.status == AssistanceTicketStatus.REOPEN }
        val closedTickets = allTickets.count { it.status == AssistanceTicketStatus.CLOSED }
        val cancelledTickets = allTickets.count { it.status == AssistanceTicketStatus.CANCELLED }

        // Calcular tiempo promedio de resolución (para tickets resueltos)
        val resolvedTicketsList = allTickets.filter { it.resolvedAt != null && it.createdAt != null }
        val averageResolutionTimeHours = if (resolvedTicketsList.isNotEmpty()) {
            resolvedTicketsList.map {
                TimeUnit.MILLISECONDS.toHours(it.resolvedAt!!.time - it.createdAt.time).toDouble()
            }.average()
        } else 0.0

        // Calcular tasa de reapertura
        val reopenRate = if (resolvedTicketsList.isNotEmpty()) {
            (reopenedTickets.toDouble() / resolvedTicketsList.size) * 100
        } else 0.0

        // Agrupar tickets por categoría
        val ticketsByCategory = allTickets.groupBy { it.category }
            .mapValues { it.value.size }

        // Agrupar tickets por prioridad (asumiendo que la prioridad es un valor numérico del 0-2)
        val ticketsByPriority = listOf(
            allTickets.count { it.priority == 0 },
            allTickets.count { it.priority == 1 },
            allTickets.count { it.priority == 2 }
        )

        return AssistanceTicketResumeDto(
            totalTickets = allTickets.size,
            pendingTickets = pendingTickets,
            assignedTickets = assignedTickets,
            inProgressTickets = inProgressTickets,
            resolvedTickets = resolvedTickets,
            reopenedTickets = reopenedTickets,
            closedTickets = closedTickets,
            cancelledTickets = cancelledTickets,
            averageResolutionTimeHours = averageResolutionTimeHours,
            reopenRate = reopenRate,
            ticketsByCategory = ticketsByCategory,
            ticketsByPriority = ticketsByPriority
        )
    }

    /**
     * Obtiene análisis detallado de planes
     */
    fun getPlanAnalysisData(): PlanAnalysisResumeDto {
        val allPlans = planRepository.findAll()
        val activeSubscriptions = subscriptionRepository.findActiveSubscriptions()

        // Distribución de suscripciones por plan
        val planDistribution = activeSubscriptions
            .filter { it.plan != null }
            .groupBy { it.plan!!.name ?: "Sin nombre" }
            .mapValues { it.value.size }

        // Distribución por velocidad
        val plansBySpeed = activeSubscriptions
            .filter { it.plan != null && it.plan!!.downloadSpeed != null }
            .groupBy { "${it.plan!!.downloadSpeed} Mbps" }
            .mapValues { it.value.size }

        // Distribución por tipo de instalación
        val plansByType = activeSubscriptions
            .filter { it.installationType != null }
            .groupBy {
                when (it.installationType) {
                    InstallationType.WIRELESS -> "Inalámbrico"
                    InstallationType.FIBER -> "Fibra"
                    InstallationType.ONLY_TV_FIBER -> "Cable TV"
                    else -> "Otro"
                }
            }
            .mapValues { it.value.size }

        // Plan más popular
        val mostPopularPlan = planDistribution.maxByOrNull { it.value }?.key ?: "Sin datos"

        // Velocidad promedio de descarga
        val averageDownloadSpeed = activeSubscriptions
            .filter { it.plan != null && it.plan!!.downloadSpeed != null }
            .map { it.plan!!.downloadSpeed!! }
            .average()

        // Promedio de ingresos por plan
        val averageRevenuePerPlan = activeSubscriptions
            .filter { it.plan != null && it.price != null }
            .groupBy { it.plan!!.name ?: "Sin nombre" }
            .mapValues { entries ->
                entries.value.map { it.price ?: 0.0 }.average()
            }

        return PlanAnalysisResumeDto(
            planDistribution = planDistribution,
            plansBySpeed = plansBySpeed,
            plansByType = plansByType,
            mostPopularPlan = mostPopularPlan,
            averageDownloadSpeed = averageDownloadSpeed,
            averageRevenuePerPlan = averageRevenuePerPlan
        )
    }

    /**
     * Obtiene datos de calidad de cartera de clientes
     */
    fun getClientQualityData(): ClientQualityResumeDto {
        val activeSubscriptions = subscriptionRepository.findActiveSubscriptions()

        // Clasificación de clientes por calificación (0-5)
        val clientsByQualification = List(6) { qualification ->
            activeSubscriptions.count { subscription ->
                subscription.getSubscriptionQualification() == qualification
            }
        }

        // Antigüedad promedio en meses
        val averageAntiquityMonths = activeSubscriptions
            .map { it.geSubscriptionAntiquity() }
            .average()

        // Porcentaje de clientes con compromiso de pago
        val paymentCommitmentPercentage = if (activeSubscriptions.isNotEmpty()) {
            (activeSubscriptions.count { it.isPaymentCommit == true }.toDouble() / activeSubscriptions.size) * 100
        } else 0.0

        // Distribución por método de pago
        val allPayments = activeSubscriptions.flatMap { it.payments }
            .filter { it.paid && it.method != null }

        val paymentMethodDistribution = allPayments
            .groupBy { it.method!! }
            .mapValues { it.value.size }

        // Deuda promedio por cliente
        val averageDebtPerClient = if (activeSubscriptions.isNotEmpty()) {
            activeSubscriptions
                .map { it.payments.filter { payment -> !payment.paid }.sumOf { payment -> payment.amountToPay } }
                .average()
        } else 0.0

        // Puntaje de satisfacción (simulado)
        // En un sistema real, esto vendría de encuestas o feedback de clientes
        val clientSatisfactionScore = 8.5

        return ClientQualityResumeDto(
            clientsByQualification = clientsByQualification,
            averageAntiquityMonths = averageAntiquityMonths,
            paymentCommitmentPercentage = paymentCommitmentPercentage,
            paymentMethodDistribution = paymentMethodDistribution,
            averageDebtPerClient = averageDebtPerClient,
            clientSatisfactionScore = clientSatisfactionScore
        )
    }

    /**
     * Obtiene análisis detallado de costos fijos
     */
    fun getFixedCostAnalysisData(): FixedCostAnalysisResumeDto {
        val allFixedCosts = fixedCostRepository.findAll()

        // Costos por categoría
        val costsByCategory = allFixedCosts
            .filter { it.enabled }
            .groupBy { it.type.name }
            .mapValues { it.value.sumOf { cost -> cost.amount } }

        // Tendencia de costos en los últimos 6 meses
        val sixMonthsAgo = Calendar.getInstance().apply {
            add(Calendar.MONTH, -6)
        }.time

        val recentCosts = allFixedCosts
            .filter { it.date.after(sixMonthsAgo) }

        val costTrend = recentCosts
            .groupBy {
                val calendar = Calendar.getInstance()
                calendar.time = it.date
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH)
                Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, 1)
                }.time
            }
            .mapValues { entry ->
                entry.value.sumOf { it.amount }
            }
            .map { CostTrendDto(it.key, it.value, "TOTAL") }
            .sortedBy { it.date }

        // Ratio costo/ingreso
        val totalCosts = allFixedCosts.filter { it.enabled }.sumOf { it.amount }
        val (currentMonthStart, nextMonthStart) = currentMonthRange()
        val billingCycleStart = currentMonthStart.minusDays(1)
        val billingCycleEnd = nextMonthStart.minusDays(1)
        val totalRevenue = paymentRepository.getGrossRevenueBetween(billingCycleStart, billingCycleEnd)
        val costToIncomeRatio = if (totalRevenue > 0) totalCosts / totalRevenue else 0.0

        // Mayor categoría de gasto
        val largestExpenseCategory = costsByCategory.maxByOrNull { it.value }?.key ?: "Sin datos"
        val largestExpenseAmount = costsByCategory.maxByOrNull { it.value }?.value ?: 0.0

        return FixedCostAnalysisResumeDto(
            costsByCategory = costsByCategory,
            costTrend = costTrend,
            costToIncomeRatio = costToIncomeRatio,
            largestExpenseCategory = largestExpenseCategory,
            largestExpenseAmount = largestExpenseAmount
        )
    }

    /**
     * Obtiene datos de órdenes de instalación
     */
    fun getInstallationOrdersData(): InstallationOrdersResumeDto {
        val allOrders = installationOrderRepository.findAll()

        // Órdenes por estado
        val pendingOrders = allOrders.count { it.status == InstallationOrderStatus.SOLICITADO }
        val completedOrders = allOrders.count { it.status == InstallationOrderStatus.CERRADO }
        val cancelledOrders = allOrders.count { it.status == InstallationOrderStatus.CANCELADO }

        // Tiempo promedio de cumplimiento (desde solicitud hasta cierre)
        val closedOrders = allOrders.filter {
            it.status == InstallationOrderStatus.CERRADO &&
                    it.createdAt != null &&
                    it.updatedAt != null
        }

        val averageFulfillmentDays = if (closedOrders.isNotEmpty()) {
            closedOrders.map {
                val start = it.createdAt!!
                val end = it.updatedAt!!
                TimeUnit.MILLISECONDS.toDays(
                    end.toLocalDate().atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toEpochMilli() -
                            start.toLocalDate().atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
                ).toDouble()
            }.average()
        } else 0.0

        // Órdenes por técnico
        val ordersByTechnician = allOrders
            .filter { it.technician != null }
            .groupBy { "${it.technician!!.name ?: "Sin nombre"} ${it.technician!!.lastName ?: ""}" }
            .mapValues { it.value.size }

        // Órdenes por lugar
        val ordersByPlace = allOrders
            .filter { it.place != null }
            .groupBy { it.place!!.name ?: "Sin nombre" }
            .mapValues { it.value.size }

        // Tasa de conversión (órdenes que se convierten en suscripciones activas)
        val totalClosedOrders = allOrders.count { it.status == InstallationOrderStatus.CERRADO }
        val convertedOrders = allOrders.count {
            it.status == InstallationOrderStatus.CERRADO &&
                    it.subscription != null &&
                    it.subscription!!.serviceStatus == ServiceStatus.ACTIVE
        }

        val conversionRate = if (totalClosedOrders > 0) {
            (convertedOrders.toDouble() / totalClosedOrders) * 100
        } else 0.0

        // Instalaciones por mes
        val sixMonthsAgo = Calendar.getInstance().apply {
            add(Calendar.MONTH, -6)
        }.time

        val recentOrders = allOrders.filter {
            it.createdAt != null &&
                    it.createdAt!!.isAfter(
                        sixMonthsAgo.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                    )
        }

        val installationsByMonth = recentOrders
            .groupBy {
                val localDate = it.createdAt!!.toLocalDate()
                java.util.Date.from(
                    localDate.withDayOfMonth(1)
                        .atStartOfDay()
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant()
                )
            }
            .mapValues { it.value.size }
            .map { InstallationMonthlyDto(it.key, it.value) }
            .sortedBy { it.date }

        return InstallationOrdersResumeDto(
            pendingOrders = pendingOrders,
            completedOrders = completedOrders,
            cancelledOrders = cancelledOrders,
            averageFulfillmentDays = averageFulfillmentDays,
            ordersByTechnician = ordersByTechnician,
            ordersByPlace = ordersByPlace,
            conversionRate = conversionRate,
            installationsByMonth = installationsByMonth
        )
    }

    /**
     * Obtiene datos de rendimiento geográfico
     */
    fun getGeographicPerformanceData(): GeographicPerformanceResumeDto {
        val allPlaces = placeRepository.findAll()
        val activeSubscriptions = subscriptionRepository.findActiveSubscriptions()
        val allTickets = assistanceTicketRepository.findAll()

        // Densidad de clientes por lugar
        val clientDensityByPlace = activeSubscriptions
            .filter { it.place != null }
            .groupBy { it.place!!.name ?: "Sin nombre" }
            .mapValues { it.value.size }

        // Ingresos por lugar
        val revenueByPlace = activeSubscriptions
            .filter { it.place != null && it.price != null }
            .groupBy { it.place!!.name ?: "Sin nombre" }
            .mapValues { entry ->
                entry.value.sumOf { it.price ?: 0.0 }
            }

        // Tasa de incidencias por lugar
        val ticketsByPlace = allTickets
            .filter { it.placeName != null }
            .groupBy { it.placeName!! }

        val incidenceRateByPlace = clientDensityByPlace.keys
            .associateWith { place ->
                val placeTickets = ticketsByPlace[place]?.size ?: 0
                val placeClients = clientDensityByPlace[place] ?: 1
                (placeTickets.toDouble() / placeClients) * 100
            }

        // Tasa de crecimiento por lugar
        // Para esto, necesitaríamos datos históricos para comparar
        // Simulación de crecimiento
        val growthRateByPlace = clientDensityByPlace.keys
            .associateWith {
                // Simulación: lugares con más clientes tienen menor crecimiento (saturación)
                val clientCount = clientDensityByPlace[it] ?: 0
                val growthRate = if (clientCount > 0) {
                    (100.0 / clientCount) * 5.0  // Fórmula simulada
                } else 0.0
                minOf(growthRate, 20.0)  // Cap de 20%
            }

        return GeographicPerformanceResumeDto(
            clientDensityByPlace = clientDensityByPlace,
            revenueByPlace = revenueByPlace,
            incidenceRateByPlace = incidenceRateByPlace,
            growthRateByPlace = growthRateByPlace
        )
    }

    /**
     * Cancelaciones agrupadas por zona/sector, para el semaforo de riesgo de cancelacion.
     */
    fun getCancellationsByZone(): Map<String, Int> {
        return subscriptionRepository.findCancelledSubscriptions()
            .filter { it.place != null }
            .groupBy { it.place!!.name ?: "Sin nombre" }
            .mapValues { it.value.size }
    }

    /**
     * Obtiene datos de rendimiento del equipo
     */
    fun getTeamPerformanceData(): TeamPerformanceResumeDto {
        val technicians = userRepository.getTechniciansByType(User.UserType.TECHNICIAN)
        val salesUsers = userRepository.getTechniciansByType(User.UserType.SALES)
        val installationOrders = installationOrderRepository.findAll()
        val tickets = assistanceTicketRepository.findAll()

        // Instalaciones por técnico
        val installationsByTechnician = technicians.associate { technician ->
            "${technician.name ?: ""} ${technician.lastName ?: ""}" to installationOrders.count {
                it.technician?.id == technician.id &&
                        it.status == InstallationOrderStatus.CERRADO
            }
        }

        // Ventas por usuario
        val salesByUser = salesUsers.associate { seller ->
            "${seller.name ?: ""} ${seller.lastName ?: ""}" to installationOrders.count {
                it.seller?.id == seller.id
            }
        }

        // Tickets por técnico
        val ticketsByTechnician = technicians.associate { technician ->
            "${technician.name ?: ""} ${technician.lastName ?: ""}" to tickets.count {
                it.responsible?.id == technician.id
            }
        }

        // Tiempo promedio de resolución por técnico
        val averageResolutionTimeByTechnician = technicians.associate { technician ->
            val technicianTickets = tickets.filter {
                it.responsible?.id == technician.id &&
                        it.resolvedAt != null &&
                        it.assignedAt != null
            }

            val avgTime = if (technicianTickets.isNotEmpty()) {
                technicianTickets.map {
                    TimeUnit.MILLISECONDS.toHours(it.resolvedAt!!.time - it.assignedAt!!.time).toDouble()
                }.average()
            } else 0.0

            "${technician.name ?: ""} ${technician.lastName ?: ""}" to avgTime
        }

        // Puntaje de eficiencia del técnico (fórmula simulada)
        val technicianEfficiencyScore = technicians.associate { technician ->
            val techName = "${technician.name ?: ""} ${technician.lastName ?: ""}"
            val installations = installationsByTechnician[techName] ?: 0
            val ticketsResolved = tickets.count {
                it.responsible?.id == technician.id &&
                        it.status == AssistanceTicketStatus.RESOLVED
            }
            val reopenedTickets = tickets.count {
                it.responsible?.id == technician.id &&
                        it.status == AssistanceTicketStatus.REOPEN
            }

            // Fórmula simulada: (instalaciones*3 + tickets resueltos) / (1 + tickets reabiertos*2)
            val score = (installations * 3 + ticketsResolved) / (1 + reopenedTickets * 2.0)
            // Normalizar entre 0-10
            techName to minOf(score / 5.0, 10.0)
        }

        return TeamPerformanceResumeDto(
            installationsByTechnician = installationsByTechnician,
            salesByUser = salesByUser,
            ticketsByTechnician = ticketsByTechnician,
            averageResolutionTimeByTechnician = averageResolutionTimeByTechnician,
            technicianEfficiencyScore = technicianEfficiencyScore
        )
    }

    private fun countConfiguredOnus(): Long {
        val client = oltGatewayHttp.ifAvailable ?: return 0L
        return try {
            val body = client.getJson("/api/olt-gateway/onus/configured", "page=0&size=1").body ?: return 0L
            objectMapper.readTree(body).path("totalElements").asLong(0L)
        } catch (_: Exception) {
            0L
        }
    }

    fun getNetworkHealthData(): NetworkHealthResumeDto {
        val devices = networkDeviceRepository.findAll()
        val napBoxes = napBoxRepository.findAll()
        val configuredOnus = countConfiguredOnus()

        val devicesByType = devices
            .groupBy { it.networkDeviceType.name }
            .mapValues { it.value.size }

        val devicesByStatus = mapOf(
            "Activo" to devices.size * 80 / 100,
            "Inactivo" to devices.size * 5 / 100,
            "Intermitente" to devices.size * 10 / 100,
            "Mantenimiento" to devices.size * 5 / 100
        )

        val nodesLoad = napBoxes.associate { napBox ->
            val napBoxId = napBox.id
            val onusCount = if (napBoxId != null && napBoxes.isNotEmpty()) {
                (configuredOnus / napBoxes.size).toInt()
            } else {
                0
            }
            val napBoxName = napBox.code
            napBoxName to minOf(onusCount.toDouble() * 10, 100.0)
        }

        // Tasa de fallos por modelo de dispositivo (simulado)
        val failureRateByDeviceModel = devices
            .groupBy { it.name ?: "Desconocido" }
            .mapValues {
                // Simulación: 1-8% de tasa de fallos
                (1 + (it.key.hashCode() % 7)) / 100.0
            }

        // Nodos sobrecargados (simulado)
        val overloadedNodes = nodesLoad
            .filter { it.value > 80.0 }
            .keys.toList()

        return NetworkHealthResumeDto(
            devicesByType = devicesByType,
            devicesByStatus = devicesByStatus,
            nodesLoad = nodesLoad,
            failureRateByDeviceModel = failureRateByDeviceModel,
            overloadedNodes = overloadedNodes
        )
    }

    /**
     * Obtiene datos del ciclo de vida del cliente
     */
    fun getClientLifecycleData(): ClientLifecycleResumeDto {
        val activeSubscriptions = subscriptionRepository.findActiveSubscriptions()
        val cancelledSubscriptions = subscriptionRepository.findCancelledSubscriptions()
        val tickets = assistanceTicketRepository.findAll()

        // Tiempo promedio hasta el primer problema reportado (días)
        val subscriptionsWithTickets = activeSubscriptions.filter { subscription ->
            tickets.any { it.subscription?.id == subscription.id }
        }

        val averageDaysToFirstIssue = if (subscriptionsWithTickets.isNotEmpty()) {
            subscriptionsWithTickets.map { subscription ->
                val firstTicket = tickets
                    .filter { it.subscription?.id == subscription.id }
                    .minByOrNull { it.createdAt }

                if (firstTicket != null && subscription.subscriptionDatetime != null) {
                    ChronoUnit.DAYS.between(
                        subscription.subscriptionDatetime,
                        firstTicket.createdAt.toInstant()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDateTime()
                    ).toDouble()
                } else 0.0
            }.filter { it > 0 }.average()
        } else 0.0

        // Tiempo promedio hasta la cancelación (días)
        val averageDaysToCancellation = if (cancelledSubscriptions.isNotEmpty()) {
            cancelledSubscriptions
                .filter { it.subscriptionDatetime != null && it.cancellationDateDatetime != null }
                .map {
                    ChronoUnit.DAYS.between(
                        it.subscriptionDatetime,
                        it.cancellationDateDatetime
                    ).toDouble()
                }
                .filter { it > 0 }
                .average()
        } else 0.0

        // Razones de cancelación (simulado)
        // En un sistema real, esto vendría de una encuesta de salida o categorización
        val cancellationReasons = mapOf(
            "Problemas técnicos" to (cancelledSubscriptions.size * 30 / 100),
            "Cambio de domicilio" to (cancelledSubscriptions.size * 25 / 100),
            "Precio" to (cancelledSubscriptions.size * 20 / 100),
            "Competencia" to (cancelledSubscriptions.size * 15 / 100),
            "Otros" to (cancelledSubscriptions.size * 10 / 100)
        )

        // Cancelaciones por plan
        val cancellationsByPlan = cancelledSubscriptions
            .filter { it.plan != null }
            .groupBy { it.plan!!.name ?: "Sin nombre" }
            .mapValues { it.value.size }

        // Cancelaciones por zona
        val cancellationsByZone = cancelledSubscriptions
            .filter { it.place != null }
            .groupBy { it.place!!.name ?: "Sin nombre" }
            .mapValues { it.value.size }

        // Tasa de cancelación estacional (simulado)
        val seasonalCancellationRate = mapOf(
            "Enero" to 5.2,
            "Febrero" to 4.8,
            "Marzo" to 3.9,
            "Abril" to 3.5,
            "Mayo" to 3.2,
            "Junio" to 3.0,
            "Julio" to 3.3,
            "Agosto" to 3.7,
            "Septiembre" to 4.1,
            "Octubre" to 4.5,
            "Noviembre" to 4.9,
            "Diciembre" to 5.5
        )

        return ClientLifecycleResumeDto(
            averageDaysToFirstIssue = averageDaysToFirstIssue,
            averageDaysToCancellation = averageDaysToCancellation,
            cancellationReasons = cancellationReasons,
            cancellationsByPlan = cancellationsByPlan,
            cancellationsByZone = cancellationsByZone,
            seasonalCancellationRate = seasonalCancellationRate
        )
    }

    /**
     * Crea un dashboard completo con todos los datos
     */
    fun createCompleteDashBoard(): DashBoardDto {
        // Obtener el dashboard básico
        val basicDashboard = createDashBoard()

        // Agregar los datos adicionales
        return basicDashboard.copy(
            assistanceTicketResume = getAssistanceTicketsData(),
            planAnalysisResume = getPlanAnalysisData(),
            clientQualityResume = getClientQualityData(),
            fixedCostAnalysisResume = getFixedCostAnalysisData(),
            installationOrdersResume = getInstallationOrdersData(),
            geographicPerformanceResume = getGeographicPerformanceData(),
            teamPerformanceResume = getTeamPerformanceData(),
            networkHealthResume = getNetworkHealthData(),
            clientLifecycleResume = getClientLifecycleData(),
        )
    }

}


data class EconomicResume(
    val grossRevenue: Double,
    val totalRaised: Double,
    val totalDiscount: Double,
    val totalToCollect: Double,
    val outLaysFromCurrentMonth: Double,
    val fixedCosts: Double,
    val corporateGrossRevenue: Double,
    val margin: Double,
    val freeCash: Double,
)

private fun Subscription.isCableTvInstallation(): Boolean {
    return plan?.type == InstallationType.ONLY_TV_FIBER
}

private fun Subscription.isWirelessInstallation() = installationType == InstallationType.WIRELESS
private fun Int.getMonthName(): String {
    return when (this) {
        0 -> "Enero"
        1 -> "Febrero"
        2 -> "Marzo"
        3 -> "Abril"
        4 -> "Mayo"
        5 -> "Junio"
        6 -> "Julio"
        7 -> "Agosto"
        8 -> "Setiembre"
        9 -> "Octubre"
        10 -> "Noviembre"
        11 -> "Diciembre"
        else -> "Unknown"
    }

}


