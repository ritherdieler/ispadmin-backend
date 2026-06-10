package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.Modules
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.dto.*
import com.dscorp.wispadmin.wispadmin.extensions.toErrorLog
import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.DashBoardService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/dashboard")
class DashBoardController @Autowired constructor(
    private val dashBoardService: DashBoardService,
    private val errorLogRepository: ErrorLogRepository,
    private val subscriptionRepository: SubscriptionRepository,
) {

    val objectError: ResponseEntity<DashBoardDto> = ResponseEntity.status(500).body(null)

    @GetMapping
    fun getDashBoard(): ResponseEntity<DashBoardDto> {
        return try {
            val dashboardDto = dashBoardService.createDashBoard()
            ResponseEntity.status(200).body(dashboardDto)
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            e.printStackTrace()
            objectError
        }
    }

    @GetMapping("/v2")
    fun getDashBoardV2(): ResponseEntity<DashBoardDto> {
        return try {
            val dashboardDto = dashBoardService.createDashBoardV2()
            ResponseEntity.status(200).body(dashboardDto)
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            e.printStackTrace()
            objectError
        }
    }

    @GetMapping("/assistance-tickets")
    fun getAssistanceTicketsData(): ResponseEntity<AssistanceTicketResumeDto> {
        return try {
            val assistanceData = dashBoardService.getAssistanceTicketsData()
            ResponseEntity.status(200).body(assistanceData)
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            e.printStackTrace()
            ResponseEntity.status(500).body(null)
        }
    }

    @GetMapping("/plan-analysis")
    fun getPlanAnalysisData(): ResponseEntity<PlanAnalysisResumeDto> {
        return try {
            val planData = dashBoardService.getPlanAnalysisData()
            ResponseEntity.status(200).body(planData)
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            e.printStackTrace()
            ResponseEntity.status(500).body(null)
        }
    }

    @GetMapping("/client-quality")
    fun getClientQualityData(): ResponseEntity<ClientQualityResumeDto> {
        return try {
            val clientData = dashBoardService.getClientQualityData()
            ResponseEntity.status(200).body(clientData)
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            e.printStackTrace()
            ResponseEntity.status(500).body(null)
        }
    }

    @GetMapping("/fixed-cost-analysis")
    fun getFixedCostAnalysisData(): ResponseEntity<FixedCostAnalysisResumeDto> {
        return try {
            val costData = dashBoardService.getFixedCostAnalysisData()
            ResponseEntity.status(200).body(costData)
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            e.printStackTrace()
            ResponseEntity.status(500).body(null)
        }
    }

    @GetMapping("/installation-orders")
    fun getInstallationOrdersData(): ResponseEntity<InstallationOrdersResumeDto> {
        return try {
            val ordersData = dashBoardService.getInstallationOrdersData()
            ResponseEntity.status(200).body(ordersData)
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            e.printStackTrace()
            ResponseEntity.status(500).body(null)
        }
    }

    @GetMapping("/geographic-performance")
    fun getGeographicPerformanceData(): ResponseEntity<GeographicPerformanceResumeDto> {
        return try {
            val geoData = dashBoardService.getGeographicPerformanceData()
            ResponseEntity.status(200).body(geoData)
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            e.printStackTrace()
            ResponseEntity.status(500).body(null)
        }
    }

    @GetMapping("/team-performance")
    fun getTeamPerformanceData(): ResponseEntity<TeamPerformanceResumeDto> {
        return try {
            val teamData = dashBoardService.getTeamPerformanceData()
            ResponseEntity.status(200).body(teamData)
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            e.printStackTrace()
            ResponseEntity.status(500).body(null)
        }
    }

    @GetMapping("/network-health")
    fun getNetworkHealthData(): ResponseEntity<NetworkHealthResumeDto> {
        return try {
            val networkData = dashBoardService.getNetworkHealthData()
            ResponseEntity.status(200).body(networkData)
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            e.printStackTrace()
            ResponseEntity.status(500).body(null)
        }
    }

    @GetMapping("/client-lifecycle")
    fun getClientLifecycleData(): ResponseEntity<ClientLifecycleResumeDto> {
        return try {
            val lifecycleData = dashBoardService.getClientLifecycleData()
            ResponseEntity.status(200).body(lifecycleData)
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            e.printStackTrace()
            ResponseEntity.status(500).body(null)
        }
    }

    @GetMapping("/complete-dashboard")
    fun getCompleteDashboard(): ResponseEntity<DashBoardDto> {
        return try {
            val dashboardDto = dashBoardService.createCompleteDashBoard()
            ResponseEntity.status(200).body(dashboardDto)
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            e.printStackTrace()
            objectError
        }
    }

    /**
     * Endpoint para obtener las ubicaciones de los clientes para el mapa de calor
     */
    @GetMapping("/subscription/locations")
    fun getSubscriptionsLocations(): ResponseEntity<List<ClienteUbicacionDto>> {
        return try {
            val subscriptions = subscriptionRepository.findAll()

            // Filtramos las suscripciones que tienen ubicación válida
            val subscriptionsWithLocation = subscriptions
                .filter { it.location != null && it.location!!.latitude != 0.0 && it.location!!.longitude != 0.0 }
                .map { subscription ->
                    // Calcular datos relevantes
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

            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(subscriptionsWithLocation)
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            e.printStackTrace()
            ResponseEntity.status(500).body(null)
        }
    }

    /**
     * Endpoint para obtener la distribución de clientes activos por tipo de servicio
     */
    @GetMapping("/subscription/active-by-type")
    fun getActiveSubscriptionsByType(): ResponseEntity<Map<String, Int>> {
        return try {
            // Obtener todas las suscripciones activas
            val activeSubscriptions = subscriptionRepository.findAll()
                .filter { it.serviceStatus == ServiceStatus.ACTIVE }

            // Agrupar por tipo de instalación usando los enums
            val subscriptionsByType = activeSubscriptions.groupBy { subscription ->

                when (subscription.installationType!!) {
                    InstallationType.FIBER -> "fiber"
                    InstallationType.WIRELESS -> "wireless"
                    InstallationType.ONLY_TV_FIBER -> "cableTv"
                }
            }

            // Obtener conteos por tipo
            val fiber = subscriptionsByType["fiber"]?.size ?: 0
            val wireless = subscriptionsByType["wireless"]?.size ?: 0
            val cableTv = subscriptionsByType["cableTv"]?.size ?: 0

            val result = mapOf(
                "fiber" to fiber,
                "wireless" to wireless,
                "cableTv" to cableTv
            )

            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(result)
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.DASHBOARD))
            e.printStackTrace()
            ResponseEntity.status(500).body(null)
        }
    }
}


