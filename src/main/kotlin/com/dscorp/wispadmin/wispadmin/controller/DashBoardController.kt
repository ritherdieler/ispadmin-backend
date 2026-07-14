package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.dto.*
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.DashBoardService
import com.dscorp.wispadmin.wispadmin.service.SubscriptionService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/dashboard")
class DashBoardController(
    private val dashBoardService: DashBoardService,
    private val subscriptionRepository: SubscriptionRepository,
    private val subscriptionService: SubscriptionService
) {

    @GetMapping
    fun getDashBoard(): ResponseEntity<DashBoardDto> =
        ResponseEntity.ok(dashBoardService.createDashBoard())

    @GetMapping("/v2")
    fun getDashBoardV2(): ResponseEntity<DashBoardDto> =
        ResponseEntity.ok(dashBoardService.createDashBoardV2())

    @GetMapping("/assistance-tickets")
    fun getAssistanceTicketsData(): ResponseEntity<AssistanceTicketResumeDto> =
        ResponseEntity.ok(dashBoardService.getAssistanceTicketsData())

    @GetMapping("/plan-analysis")
    fun getPlanAnalysisData(): ResponseEntity<PlanAnalysisResumeDto> =
        ResponseEntity.ok(dashBoardService.getPlanAnalysisData())

    @GetMapping("/client-quality")
    fun getClientQualityData(): ResponseEntity<ClientQualityResumeDto> =
        ResponseEntity.ok(dashBoardService.getClientQualityData())

    @GetMapping("/fixed-cost-analysis")
    fun getFixedCostAnalysisData(): ResponseEntity<FixedCostAnalysisResumeDto> =
        ResponseEntity.ok(dashBoardService.getFixedCostAnalysisData())

    @GetMapping("/installation-orders")
    fun getInstallationOrdersData(): ResponseEntity<InstallationOrdersResumeDto> =
        ResponseEntity.ok(dashBoardService.getInstallationOrdersData())

    @GetMapping("/geographic-performance")
    fun getGeographicPerformanceData(): ResponseEntity<GeographicPerformanceResumeDto> =
        ResponseEntity.ok(dashBoardService.getGeographicPerformanceData())

    @GetMapping("/team-performance")
    fun getTeamPerformanceData(): ResponseEntity<TeamPerformanceResumeDto> =
        ResponseEntity.ok(dashBoardService.getTeamPerformanceData())

    @GetMapping("/network-health")
    fun getNetworkHealthData(): ResponseEntity<NetworkHealthResumeDto> =
        ResponseEntity.ok(dashBoardService.getNetworkHealthData())

    @GetMapping("/client-lifecycle")
    fun getClientLifecycleData(): ResponseEntity<ClientLifecycleResumeDto> =
        ResponseEntity.ok(dashBoardService.getClientLifecycleData())

    @GetMapping("/complete-dashboard")
    fun getCompleteDashboard(): ResponseEntity<DashBoardDto> =
        ResponseEntity.ok(dashBoardService.createCompleteDashBoard())

    @GetMapping("/subscription/locations")
    fun getSubscriptionsLocations(): ResponseEntity<List<ClienteUbicacionDto>> {
        val subscriptions = subscriptionService.findAllForListing()

        val subscriptionsWithLocation = subscriptions
            .filter { it.location != null && it.location!!.latitude != 0.0 && it.location!!.longitude != 0.0 }
            .map { subscription ->
                val pendingInvoices = subscription.payments
                    ?.filter { !it.paid }
                    ?.size ?: 0

                val totalDebt = subscription.payments
                    ?.filter { !it.paid }
                    ?.sumOf { it.amountToPay }
                    ?: 0.0

                ClienteUbicacionDto(
                    id = subscription.id!!,
                    firstName = subscription.firstName ?: "",
                    lastName = subscription.lastName ?: "",
                    plan = subscription.plan?.name,
                    location = subscription.location!!.toDto(),
                    serviceStatus = subscription.serviceStatus,
                    address = subscription.address,
                    phone = subscription.phone,
                    dni = subscription.dni,
                    ip = subscription.ip,
                    subscriptionDate = subscription.subscriptionDatetime
                        ?.atZone(java.time.ZoneId.systemDefault())
                        ?.toInstant()
                        ?.toEpochMilli(),
                    lastCutOffDate = subscription.lastCutOffDate,
                    pendingInvoiceQuantity = pendingInvoices,
                    totalDebt = totalDebt,
                    place = subscription.place?.name,
                    installationType = subscription.installationType?.name
                )
            }

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(subscriptionsWithLocation)
    }

    @GetMapping("/subscription/active-by-type")
    fun getActiveSubscriptionsByType(): ResponseEntity<Map<String, Int>> {
        val activeSubscriptions = subscriptionRepository.findAll()
            .filter { it.serviceStatus == ServiceStatus.ACTIVE }

        val subscriptionsByType = activeSubscriptions.groupBy { subscription ->
            when (subscription.installationType!!) {
                InstallationType.FIBER -> "fiber"
                InstallationType.WIRELESS -> "wireless"
                InstallationType.ONLY_TV_FIBER -> "cableTv"
            }
        }

        val fiber = subscriptionsByType["fiber"]?.size ?: 0
        val wireless = subscriptionsByType["wireless"]?.size ?: 0
        val cableTv = subscriptionsByType["cableTv"]?.size ?: 0

        val result = mapOf(
            "fiber" to fiber,
            "wireless" to wireless,
            "cableTv" to cableTv
        )

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(result)
    }
}
