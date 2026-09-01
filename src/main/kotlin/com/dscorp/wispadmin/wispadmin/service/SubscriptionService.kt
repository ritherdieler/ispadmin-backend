package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.routeros.port.MikrotikException
import com.dscorp.wispadmin.wispadmin.controller.toAuthorizationRequest
import com.dscorp.wispadmin.wispadmin.controller.toErrorLog
import com.dscorp.wispadmin.wispadmin.data.model.*
import com.dscorp.wispadmin.wispadmin.dto.AddressListGenerationResultDto
import com.dscorp.wispadmin.wispadmin.dto.CutServiceSummaryDto
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionDto
import com.dscorp.wispadmin.wispadmin.extensions.executeCommand
import com.dscorp.wispadmin.wispadmin.repository.*
import com.dscorp.wispadmin.wispadmin.requestbody.MigrationRequest
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import com.dscorp.wispadmin.wispadmin.requestbody.UpdateSubscriptionDataBody
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.MoveOnuRequest
import com.dscorp.wispadmin.wispadmin.service.onu.OnuOperationsPort
import com.dscorp.wispadmin.wispadmin.service.mikrotik.IAddressListManager
import com.dscorp.wispadmin.wispadmin.service.mikrotik.IMikroTikService
import com.dscorp.wispadmin.wispadmin.service.mikrotik.IQueueManager
import com.dscorp.wispadmin.wispadmin.service.mikrotik.QueueCreationStats
import com.dscorp.wispadmin.wispadmin.service.subscription.IServiceCutManager
import com.dscorp.wispadmin.wispadmin.service.subscription.IServiceReactivationManager
import com.dscorp.wispadmin.wispadmin.service.subscription.strategies.FiberInstallationStrategy
import com.dscorp.wispadmin.wispadmin.service.subscription.strategies.InstallationResult
import com.dscorp.wispadmin.wispadmin.service.subscription.strategies.InstallationStrategyFactory
import com.dscorp.wispadmin.wispadmin.service.validators.ISubscriptionValidator
import com.dscorp.wispadmin.wispadmin.util.fcm.FcmMessage.FcmMessageType
import com.dscorp.wispadmin.wispadmin.util.isValidIpAddress
import com.dscorp.wispadmin.wispadmin.service.subscription.SubscriptionRegisteredEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*
import java.util.concurrent.CompletableFuture
import java.time.LocalDateTime
@Service
class SubscriptionService(
    private val repository: SubscriptionRepository,
    private val ipPoolRepository: IpPoolRepository,
    private val networkDeviceRepository: NetworkDeviceRepository,
    private val planRepository: PlanRepository,
    private val placeRepository: PlaceRepository,
    private val napBoxRepository: NapBoxRepository,
    private val onuService: OnuOperationsPort,
    private val onuRepository: OnuRepository,
    private val installationOrderRepository: InstallationOrderRepository,
    private val notificationService: NotificationService,
    private val subscriptionLogRepository: SubscriptionLogRepository,
    private val errorLogRepository: ErrorLogRepository,
    val borneManagementService: BorneManagementService,
    private val mikrotikService: IMikroTikService,
    private val queueManager: IQueueManager,
    private val addressListManager: IAddressListManager,
    private val serviceCutManager: IServiceCutManager,
    private val serviceReactivationManager: IServiceReactivationManager,
    private val subscriptionValidator: ISubscriptionValidator,
    private val paymentRepository: PaymentRepository,
    private val installationStrategyFactory: InstallationStrategyFactory,
    private val fiberInstallationStrategy: FiberInstallationStrategy,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val cancelledOnuReuseService: CancelledOnuReuseService,
    private val subscriptionProvisionService: SubscriptionProvisionService,
    private val ipAllocationService: IpAllocationService
) {
    private val logger = LoggerFactory.getLogger(SubscriptionService::class.java)

    fun findExistingSubscriptionByClientRequestId(clientRequestId: String?): Subscription? {
        val normalized = clientRequestId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return repository.findByClientRequestId(normalized).orElse(null)
    }

    fun createSubscriptionsSimpleQueue(): CompletableFuture<QueueCreationStats> {
        return queueManager.createSubscriptionsSimpleQueue()
    }

    @Transactional(readOnly = true)
    fun findAllForListing(): List<Subscription> {
        val subscriptions = repository.findAllWithCoreRelations()
        if (subscriptions.isEmpty()) return subscriptions

        val paymentsBySubscriptionId = paymentRepository
            .findBySubscriptionIdInFetchResponsible(subscriptions.mapNotNull { it.id })
            .groupBy { it.subscription?.id }

        subscriptions.forEach { subscription ->
            subscription.payments = paymentsBySubscriptionId[subscription.id].orEmpty().toMutableSet()
        }

        return subscriptions
    }

    @Transactional(readOnly = true)
    fun getAllSubscriptionsForList(): List<SubscriptionDto> =
        findAllForListing().map { it.toDto() }

    fun changeNapBox(request: MoveOnuRequest): Subscription {
        val subscription = repository.findById(request.subscriptionId).get()
        val currentSubscriptionOnu = subscription.fiberOnu
        val newNapBox = napBoxRepository.findById(request.newNapBoxId).get()
        subscription.napBox = newNapBox
        
        if (currentSubscriptionOnu == null) throw Exception("No se puede mover la ONU, no existe la ONU en la suscripción")
        onuService.moveOnu(request, currentSubscriptionOnu, newNapBox)

        subscription.napBox = newNapBox

        return repository.save(subscription)
    }

    fun rebootFiberOnu(subscriptionId: Int): Subscription {
        val subscription = repository.findById(subscriptionId).orElseThrow {
            IllegalArgumentException("Suscripción no encontrada")
        }
        val installationType = subscription.installationType
            ?: throw IllegalArgumentException("Tipo de instalación no definido")
        if (installationType != InstallationType.FIBER && installationType != InstallationType.ONLY_TV_FIBER) {
            throw IllegalArgumentException("Solo suscripciones de internet por fibra o TV cable por fibra pueden reiniciar la ONU")
        }
        val onu = subscription.fiberOnu
            ?: throw IllegalArgumentException("La suscripción no tiene ONU registrada")
        onuService.rebootOnuBySn(onu.sn)
        return subscription
    }

    @Transactional
    fun migrateToFiber(request: MigrationRequest): Subscription {
        try {
            val subscription = repository.findById(request.subscriptionId).get()
            val plan = planRepository.findById(request.planId).get()
            val authorizationRequest = request.onu.toAuthorizationRequest(
                customerFullName = subscription.getFullName(),
                vlan = fiberInstallationStrategy.resolveVlan(subscription)
            )
            subscription.apply {
                this.plan = plan
                isMigration = true
                installationType = InstallationType.FIBER
                fiberOnu = request.onu.toModel()
                migrationDate = Date()
                migrationNote = request.notes
                migrationPrice = request.price
            }
            repository.save(subscription)
            
            subscription.hostDevice!!.executeCommand { connection ->
                queueManager.recreateQueueForSubscription(connection, subscription)
            }

            cancelledOnuReuseService.authorizeWithCancelledReuse(authorizationRequest)

            return subscription
        } catch (e: Exception) {
            throw Exception("No se pudo migrar el servicio: ${e.message}", e)
        }

    }
    @Transactional
    fun registerSubscription(
        newSubscription: SubscriptionRequest,
        onSuccess: (subscription: Subscription) -> Unit
    ): SubscriptionDto {
        var subscription: Subscription? = null
        var queueAdded = false
        var onuAuthorized = false
        var onuSn: String? = null

        return try {
            findExistingSubscriptionByClientRequestId(newSubscription.clientRequestId)?.let { existing ->
                subscriptionProvisionService.reconcile(existing, newSubscription)
                return existing.toDto().copy(alreadyRegistered = true)
            }

            subscriptionValidator.validateSubscriptionRequest(newSubscription)

            val ipAssignment = resolveIpAssignment(newSubscription)
            val subscriptionToSave = createSubscriptionEntity(newSubscription, ipAssignment)
            subscriptionProvisionService.initializeStatuses(
                subscriptionToSave,
                newSubscription.installationType
            )

            if (newSubscription.installationType == InstallationType.FIBER ||
                (newSubscription.installationType == InstallationType.ONLY_TV_FIBER &&
                    newSubscription.onu != null)
            ) {
                processOnuForFiber(subscriptionToSave, newSubscription)
            }

            if (newSubscription.installationType in listOf(InstallationType.FIBER, InstallationType.ONLY_TV_FIBER)) {
                try {
                    val assignedBorne =
                        borneManagementService.validateAndAssignBorne(subscriptionToSave, newSubscription.borneNumber)
                    subscriptionToSave.borneNumber = assignedBorne
                    logger.info("Borne $assignedBorne asignado para suscripción ${subscriptionToSave.id}")
                } catch (e: NoAvailableBornesException) {
                    throw Exception(
                        "No se puede registrar la suscripción: ${e.message}. " +
                                "Por favor, seleccione una NAP Box diferente o contacte al administrador."
                    )
                }
            }

            subscription = repository.save(subscriptionToSave)
            logger.info(
                "Suscripción creada exitosamente", mapOf(
                    "subscriptionId" to subscription.id,
                    "customerName" to subscription.getFullName(),
                    "installationType" to subscription.installationType
                )
            )

            val device = networkDeviceRepository.findById(subscription.hostDevice!!.id).get()
            val plan = planRepository.findById(subscription.plan!!.id).get()
            val place = placeRepository.findById(subscription.place!!.id).get()
            subscription.hostDevice = device
            subscription.plan = plan
            subscription.place = place

            val strategy = installationStrategyFactory.getStrategy(newSubscription.installationType)
            val installationResult = try {
                val result = strategy.processInstallation(subscription, newSubscription, device, plan, place)
                queueAdded = result.queueAdded
                onuAuthorized = result.onuAuthorized
                onuSn = result.onuSn
                result
            } catch (ex: Exception) {
                logger.error("Error de provisión de red tras persistir suscripción ${subscription.id}", ex)
                persistProvisionError(ex)
                InstallationResult(
                    queueAdded = false,
                    onuAuthorized = false,
                    mikrotikError = ex.message,
                    oltError = ex.message
                )
            }
            subscriptionProvisionService.applyInstallationResult(
                subscription = subscription,
                result = installationResult,
                installationType = newSubscription.installationType
            )
            subscription = repository.save(subscription)

            newSubscription.installationOrderId?.let { orderId ->
                processInstallationOrderCompletion(orderId, subscription)
            }

            subscription.id?.let { subscriptionId ->
                applicationEventPublisher.publishEvent(SubscriptionRegisteredEvent(subscriptionId))
            }

            onSuccess(subscription.copy(plan = plan))
            subscription.toDto()
        } catch (ex: Exception) {
            ex.printStackTrace()
            handleRegistrationError(ex, subscription, queueAdded, onuAuthorized, onuSn)
        }
    }

    private fun completePendingInstallation(
        existing: Subscription,
        request: SubscriptionRequest
    ) {
        subscriptionProvisionService.reconcile(existing, request)
    }

    private fun persistProvisionError(ex: Exception) {
        try {
            errorLogRepository.save(ex.toErrorLog(Modules.SUBSCRIPTION))
        } catch (logError: Exception) {
            logger.warn("No se pudo guardar el error de provisión MikroTik", logError)
        }
    }

    private fun createSubscriptionEntity(
        newSubscription: SubscriptionRequest,
        freeIp: Pair<String, IpPool>
    ): Subscription {
        return newSubscription.toModel().apply {
            subscriptionDatetime = LocalDateTime.now()
            clientRequestId = newSubscription.clientRequestId?.trim()?.takeIf { it.isNotEmpty() }

            this.ip = freeIp.first
            this.ipPool = freeIp.second

            if (newSubscription.installationOrderId != null) {
                val installationOrder =
                    installationOrderRepository.findById(newSubscription.installationOrderId!!).orElse(null)
                this.installationOrder = installationOrder
            }
        }
    }

    private fun processOnuForFiber(subscriptionToSave: Subscription, newSubscription: SubscriptionRequest) {
        val existingOnu = onuRepository.findById(newSubscription.onu!!.sn)
        if (existingOnu.isPresent) {
            val updatedOnu = existingOnu.get().apply {
                this.board = newSubscription.onu!!.board
                this.pon_type = newSubscription.onu!!.pon_type
                this.port = newSubscription.onu!!.port
                this.olt_id = newSubscription.onu!!.olt_id
                this.onu_type_id = newSubscription.onu!!.onu_type_id
                this.onu_type_name = newSubscription.onu!!.onu_type_name
            }
            subscriptionToSave.fiberOnu = updatedOnu
        }
    }

    private fun handleRegistrationError(
        ex: Exception,
        subscription: Subscription?,
        queueAdded: Boolean,
        onuAuthorized: Boolean,
        onuSn: String?
    ): Nothing {
        logger.error("Error al registrar suscripción", ex)

        if (queueAdded && subscription?.ip != null) {
            cleanupMikroTikQueue(subscription)
        }

        if (onuAuthorized && onuSn != null) {
            cleanupOnu(onuSn)
        }

        errorLogRepository.save(ex.toErrorLog(Modules.SUBSCRIPTION))
        throw RuntimeException("Error al registrar la suscripción: ${ex.message}", ex)
    }

    private fun cleanupMikroTikQueue(subscription: Subscription) {
        try {
            val device = networkDeviceRepository.findById(subscription.hostDevice!!.id).get()
            device.executeCommand { connection ->
                mikrotikService.findAndRemoveQueueByIp(connection, subscription.ip!!)
            }
        } catch (cleanupEx: Exception) {
            logger.error("Error limpiando queue de MikroTik", cleanupEx)
            errorLogRepository.save(cleanupEx.toErrorLog(Modules.SUBSCRIPTION))
        }
    }

    private fun cleanupOnu(onuSn: String) {
        try {
            onuService.deleteOnu(onuSn)
        } catch (onuCleanupEx: Exception) {
            logger.error("Error limpiando ONU", onuCleanupEx)
            errorLogRepository.save(onuCleanupEx.toErrorLog(Modules.SUBSCRIPTION))
        }
    }

    fun getBorneValidationForReactivation(subscriptionId: Int): BorneValidationResult {
        val subscription = repository.findById(subscriptionId).get()
        return borneManagementService.validateReactivationBorne(subscription)
    }

    private fun processInstallationOrderCompletion(orderId: Int, subscription: Subscription) {
        try {
            val installationOrder = installationOrderRepository.findById(orderId).orElse(null)
                ?: return

            installationOrder.status = InstallationOrderStatus.CERRADO
            installationOrderRepository.save(installationOrder)

            val customerName = "${subscription.firstName} ${subscription.lastName}"
            val customerAddress = subscription.address ?: ""
            val placeName = subscription.place?.name ?: ""
            val fullAddress = "$placeName, $customerAddress"

            installationOrder.seller?.id?.let { sellerId ->
                notificationService.sendUserNotification(
                    userId = sellerId,
                    title = "Instalación Completada",
                    message = "La instalación para el cliente $customerName en $fullAddress ha sido completada exitosamente.",
                    type = FcmMessageType.SALES_CLOSED_INSTALLATION_ORDER,
                    id = orderId.toString(),
                    data = mapOf(
                        "subscriptionId" to subscription.id,
                        "installationOrderId" to orderId,
                        "customerName" to customerName,
                        "address" to fullAddress
                    )
                )
            }

            installationOrder.assignedBy?.id?.let { assignedById ->
                notificationService.sendUserNotification(
                    userId = assignedById,
                    title = "Instalación Completada",
                    message = "La instalación programada para $customerName en $fullAddress ha sido finalizada.",
                    type = FcmMessageType.SALES_CLOSED_INSTALLATION_ORDER,
                    id = orderId.toString(),
                    data = mapOf(
                        "subscriptionId" to subscription.id,
                        "installationOrderId" to orderId,
                        "customerName" to customerName,
                        "address" to fullAddress
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))
        }
    }

    private fun resolveIpAssignment(request: SubscriptionRequest): Pair<String, IpPool> {
        var assignment = ipAllocationService.allocate(
            hostDeviceId = request.hostDeviceId,
            preferredIp = request.clientIpAddress
        )
        if (repository.existsByIpAndServiceStatus(assignment.first, ServiceStatus.ACTIVE)) {
            ipAllocationService.reportCollision(
                ip = assignment.first,
                reason = IpAllocationService.REASON_ACTIVE_SUBSCRIPTION,
                hostDeviceId = request.hostDeviceId
            )
            assignment = ipAllocationService.allocate(hostDeviceId = request.hostDeviceId)
        }
        if (repository.existsByIpAndServiceStatus(assignment.first, ServiceStatus.ACTIVE)) {
            throw IllegalStateException("No more ips available")
        }
        return assignment
    }

    fun getFreeIp(hostDeviceId: Int? = null): Pair<String, IpPool> {
        return ipAllocationService.allocate(hostDeviceId = hostDeviceId)
    }

    @Transactional
    fun cutInternetService(): CutServiceSummaryDto {
        return serviceCutManager.cutInternetService()
    }

    fun updateSubscriptionPlan(subscriptionId: Int, planId: Int): Subscription {
        val subscription = repository.findById(subscriptionId).get()
        val newPlan = planRepository.findById(planId).get()
        subscription.hostDevice!!.executeCommand { session ->
            val result = session.print("/queue/simple", mapOf("target" to "${subscription.ip}/32"))
            result.lastOrNull()?.get(".id")?.let { id ->
                session.set(
                    "/queue/simple",
                    id,
                    mapOf("max-limit" to "${newPlan.uploadSpeed}M/${newPlan.downloadSpeed}M")
                )
            }
        }
        subscription.plan = newPlan
        return repository.save(subscription)
    }

    fun updateSubscriptionData(updateSubscriptionData: UpdateSubscriptionDataBody) {
        val subscription = repository.findById(updateSubscriptionData.subscriptionId).get()
        subscription.apply {
            firstName = updateSubscriptionData.firstName
            lastName = updateSubscriptionData.lastName
            dni = updateSubscriptionData.dni
            address = updateSubscriptionData.address
            phone = updateSubscriptionData.phone
            place = placeRepository.findById(updateSubscriptionData.placeId).get()
            location = updateSubscriptionData.location
        }
        repository.save(subscription)
    }

    @Transactional
    fun updateSubscription(updatedSubscription: SubscriptionRequest): Pair<Subscription, SubscriptionActionType> {
        val subscription = repository.findById(updatedSubscription.id!!).get()
        val oldPlanId = subscription.plan?.id
        val newPlanId = updatedSubscription.planId
        val oldFirstName = subscription.firstName
        val oldLastName = subscription.lastName
        val oldPlace = subscription.place

        val actionType = if (oldPlanId != newPlanId) {
            SubscriptionActionType.CHANGE_PLAN
        } else {
            SubscriptionActionType.UPDATE_SUBSCRIPTION
        }

        subscription.apply {
            firstName = updatedSubscription.firstName
            lastName = updatedSubscription.lastName
            dni = updatedSubscription.dni
            password = updatedSubscription.password ?: dni
            phone = updatedSubscription.phone
            address = updatedSubscription.address
            location = GeoLocation(updatedSubscription.location.latitude, updatedSubscription.location.longitude)
            plan = planRepository.findById(updatedSubscription.planId).get()
            place = placeRepository.findById(updatedSubscription.placeId).get()
            additionalDevices = updatedSubscription.additionalDeviceIds.map { NetworkDevice(id = it) }
            hostDevice = networkDeviceRepository.findById(updatedSubscription.hostDeviceId).get()
            napBox = updatedSubscription.napBoxId?.let { napBoxRepository.findById(it).get() }
            price = updatedSubscription.price
            note = updatedSubscription.note
            autoCut = updatedSubscription.autoCut
        }

        val shouldUpdateQueue = oldPlanId != newPlanId ||
                oldFirstName != subscription.firstName ||
                oldLastName != subscription.lastName ||
                oldPlace?.id != subscription.place?.id

        if (shouldUpdateQueue) {
            queueManager.updateMikroTikQueue(subscription)
        }

        val savedSubscription = repository.save(subscription)
        return Pair(savedSubscription, actionType)
    }

    @Transactional
    fun registerPaymentCommitment(subscriptionId: Int) {
        serviceReactivationManager.registerPaymentCommitment(subscriptionId)
    }

    @Transactional
    fun reactivateService(
        subscriptionId: Int,
        responsibleId: Int,
        notes: String? = null,
        newBorneNumber: String? = null
    ) {
        serviceReactivationManager.reactivateService(subscriptionId, responsibleId, notes, newBorneNumber)
    }

    @Transactional
    fun restoreInternetConnection(
        subscriptionId: Int,
        responsibleId: Int,
        notes: String? = null
    ) {
        // Validar que el cliente tenga menos de 2 facturas pendientes
        val pendingPaymentsCount = paymentRepository.findPendingPaymentsBySubscriptionId(subscriptionId)
        
        if (pendingPaymentsCount >= 2) {
            throw IllegalStateException("No se puede restablecer el internet a un cliente que tiene $pendingPaymentsCount facturas pendientes. Debe tener menos de 2 facturas pendientes.")
        }

        // Obtener la suscripción
        val subscription = repository.findById(subscriptionId)
            .orElseThrow { IllegalArgumentException("Suscripción no encontrada con ID: $subscriptionId") }

        // Registrar log de la operación
        val logEntry = SubscriptionLog(
            subscription = subscription,
            actionType = SubscriptionActionType.RESTORE_INTERNET_CONNECTION
        )

        subscriptionLogRepository.save(logEntry)
        
        logger.info("Conexión a internet restablecida para suscripción $subscriptionId por usuario $responsibleId. Notas: ${notes ?: "Sin notas"}")
    }

    @Transactional
    fun cancelService(idSubscription: Int, onSuccess: (subscription: Subscription) -> Unit) {
        val subscription = repository.findById(idSubscription).get()

        if (subscription.serviceStatus == ServiceStatus.CANCELLED) {
            return
        }

        borneManagementService.releaseBorne(subscription)

        repository.cancelService(
            idSubscription = idSubscription,
            cancellationDateDatetime = LocalDateTime.now()
        )

        subscription.hostDevice?.let {
            if (subscription.ip?.isValidIpAddress() == true)
                it.executeCommand { connection ->
                    mikrotikService.addIpToDebtorsListIfNotExists(connection, subscription.ip!!, subscription.getFullName().uppercase())
                    mikrotikService.createFirewallDropRule(connection)
                }
        }
        onSuccess(subscription)
    }

    @Transactional
    fun updateSubscriptionLocation(subscriptionId: Int, location: GeoLocation) {
        try {
            val subscription = repository.findById(subscriptionId).orElseThrow {
                Exception("Suscripción no encontrada con ID: $subscriptionId")
            }

            subscription.location = location
            repository.save(subscription)

            subscriptionLogRepository.save(
                SubscriptionLog(
                    subscription = subscription,
                    actionType = SubscriptionActionType.UPDATE_LOCATION,
                    planName = subscription.plan?.name,
                    planPrice = subscription.plan?.price ?: 0.0,
                    planId = subscription.plan?.id
                )
            )

            println("✅ Ubicación actualizada para suscripción $subscriptionId: Lat ${location.latitude}, Lng ${location.longitude}")
        } catch (e: Exception) {
            println("❌ Error al actualizar ubicación para suscripción $subscriptionId: ${e.message}")
            throw Exception("Error al actualizar la ubicación: ${e.message}")
        }
    }

    fun generateAddressListForCancelledSubscriptions(): AddressListGenerationResultDto {
        return addressListManager.generateAddressListForCancelledSubscriptions()
    }
}

