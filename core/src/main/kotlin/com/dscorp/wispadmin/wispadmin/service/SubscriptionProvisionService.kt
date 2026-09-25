package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.controller.toErrorLog
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.MikrotikProvisionStatus
import com.dscorp.wispadmin.wispadmin.data.model.Modules
import com.dscorp.wispadmin.wispadmin.data.model.OltProvisionStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus
import com.dscorp.wispadmin.wispadmin.dto.OnuDto
import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import com.dscorp.wispadmin.wispadmin.repository.NetworkDeviceRepository
import com.dscorp.wispadmin.wispadmin.repository.PlaceRepository
import com.dscorp.wispadmin.wispadmin.repository.PlanRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayOnuActivationClient
import com.dscorp.wispadmin.wispadmin.service.genieacs.GenieAcsSubscriptionTagger
import com.dscorp.wispadmin.wispadmin.service.genieacs.GenieAcsSubscriptionTags
import com.dscorp.wispadmin.wispadmin.service.genieacs.SubscriptionAcsSyncService
import com.dscorp.wispadmin.wispadmin.service.genieacs.Tr069ProvisionOutcome
import com.dscorp.wispadmin.wispadmin.service.mikrotik.PppoeAccessService
import com.dscorp.wispadmin.wispadmin.service.subscription.strategies.InstallationResult
import com.dscorp.wispadmin.wispadmin.service.subscription.strategies.InstallationStrategyFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class SubscriptionProvisionService(
    private val repository: SubscriptionRepository,
    private val networkDeviceRepository: NetworkDeviceRepository,
    private val planRepository: PlanRepository,
    private val placeRepository: PlaceRepository,
    private val installationStrategyFactory: InstallationStrategyFactory,
    private val errorLogRepository: ErrorLogRepository,
    private val gatewayActivation: ObjectProvider<GatewayOnuActivationClient>,
    private val pppoeAccessService: PppoeAccessService,
    @Value("\${olt.gateway.client-enabled:false}") private val cpeEnabled: Boolean = false,
    private val acsSyncService: SubscriptionAcsSyncService? = null,
    private val acsTagger: GenieAcsSubscriptionTagger? = null,
) {
    private val logger = LoggerFactory.getLogger(SubscriptionProvisionService::class.java)

    fun initializeStatuses(subscription: Subscription, installationType: InstallationType) {
        when (installationType) {
            InstallationType.WIRELESS -> {
                subscription.mikrotikProvisionStatus = MikrotikProvisionStatus.PENDING
                subscription.oltProvisionStatus = OltProvisionStatus.NA
                subscription.tr069ProvisionStatus = Tr069ProvisionStatus.NA
            }
            InstallationType.FIBER -> {
                subscription.mikrotikProvisionStatus = MikrotikProvisionStatus.PENDING
                subscription.oltProvisionStatus = OltProvisionStatus.PENDING
                subscription.tr069ProvisionStatus =
                    if (cpeProvisionEnabled()) Tr069ProvisionStatus.PENDING
                    else Tr069ProvisionStatus.NA
            }
            InstallationType.ONLY_TV_FIBER -> {
                subscription.mikrotikProvisionStatus = MikrotikProvisionStatus.COMPLETE
                val hasOnu = !subscription.fiberOnuSn.isNullOrBlank()
                subscription.oltProvisionStatus =
                    if (hasOnu) OltProvisionStatus.PENDING else OltProvisionStatus.NA
                subscription.tr069ProvisionStatus = when {
                    hasOnu && cpeProvisionEnabled() -> Tr069ProvisionStatus.PENDING
                    else -> Tr069ProvisionStatus.NA
                }
            }
        }
    }

    fun applyInstallationResult(
        subscription: Subscription,
        result: InstallationResult,
        installationType: InstallationType,
        thrownError: String? = null
    ) {
        when (installationType) {
            InstallationType.WIRELESS -> {
                subscription.mikrotikProvisionStatus =
                    if (result.queueAdded) MikrotikProvisionStatus.COMPLETE
                    else MikrotikProvisionStatus.PENDING
            }
            InstallationType.FIBER -> {
                subscription.mikrotikProvisionStatus =
                    if (result.queueAdded) MikrotikProvisionStatus.COMPLETE
                    else MikrotikProvisionStatus.PENDING
                subscription.oltProvisionStatus = mapOltStatus(result)
                mapCpeStatus(subscription, result.cpeStatus)
            }
            InstallationType.ONLY_TV_FIBER -> {
                subscription.mikrotikProvisionStatus = MikrotikProvisionStatus.COMPLETE
                val hasOnu = !subscription.fiberOnuSn.isNullOrBlank() || result.onuSn != null
                if (hasOnu) {
                    subscription.oltProvisionStatus = mapOltStatus(result)
                    mapCpeStatus(subscription, result.cpeStatus)
                } else {
                    subscription.oltProvisionStatus = OltProvisionStatus.NA
                    subscription.tr069ProvisionStatus = Tr069ProvisionStatus.NA
                }
            }
        }

        val errors = listOfNotNull(result.mikrotikError, result.oltError, thrownError)
            .joinToString(" | ")
            .ifBlank { null }
        if (errors != null) {
            subscription.provisionLastError = errors.take(500)
        } else if (!subscription.isMikrotikOrOltPending()) {
            subscription.provisionLastError = null
            if (subscription.tr069ProvisionStatus != Tr069ProvisionStatus.PENDING) {
                subscription.provisionNextAttemptAt = null
            }
        }

        if (subscription.isMikrotikOrOltPending()) {
            scheduleNextAttempt(subscription)
        } else if (
            subscription.tr069ProvisionStatus == Tr069ProvisionStatus.PENDING &&
            subscription.oltProvisionStatus == OltProvisionStatus.COMPLETE &&
            subscription.provisionNextAttemptAt == null
        ) {
            // OLT already OK; keep a retry window for GenieACS TR-069.
            scheduleNextAttempt(subscription)
        }
    }

    fun scheduleNextAttempt(subscription: Subscription) {
        val attempts = subscription.provisionAttemptCount ?: 0
        if (attempts >= MAX_ATTEMPTS) {
            if (subscription.mikrotikProvisionStatus == MikrotikProvisionStatus.PENDING) {
                subscription.mikrotikProvisionStatus = MikrotikProvisionStatus.FAILED
            }
            if (subscription.oltProvisionStatus == OltProvisionStatus.PENDING) {
                subscription.oltProvisionStatus = OltProvisionStatus.FAILED
            }
            subscription.provisionNextAttemptAt = null
            return
        }

        val delayMinutes = when {
            attempts <= 0 -> 5L
            attempts == 1 -> 15L
            else -> 30L
        }
        subscription.provisionAttemptCount = attempts + 1
        subscription.provisionNextAttemptAt = LocalDateTime.now().plusMinutes(delayMinutes)
    }

    @Transactional
    fun reconcile(subscription: Subscription, request: SubscriptionRequest): Subscription {
        val installationType = subscription.installationType ?: request.installationType
        if (subscription.mikrotikProvisionStatus == null && subscription.oltProvisionStatus == null) {
            initializeStatuses(subscription, installationType)
        }
        val needsMikrotikOrOlt =
            subscription.mikrotikProvisionStatus == MikrotikProvisionStatus.PENDING ||
                subscription.mikrotikProvisionStatus == MikrotikProvisionStatus.FAILED ||
                subscription.oltProvisionStatus == OltProvisionStatus.PENDING ||
                subscription.oltProvisionStatus == OltProvisionStatus.FAILED

        if (needsMikrotikOrOlt) {
            val deviceId = subscription.hostDevice?.id ?: request.hostDeviceId
            val planId = subscription.plan?.id ?: request.planId
            val placeId = subscription.place?.id ?: request.placeId
            val device = networkDeviceRepository.findById(deviceId).get()
            val plan = planRepository.findById(planId).get()
            val place = placeRepository.findById(placeId).get()
            subscription.hostDevice = device
            subscription.plan = plan
            subscription.place = place

            val strategy = installationStrategyFactory.getStrategy(installationType)
            val result = try {
                strategy.processInstallation(subscription, request, device, plan, place)
            } catch (ex: Exception) {
                logger.warn("Fallo reconciliando provisión para suscripción ${subscription.id}", ex)
                persistProvisionError(ex)
                InstallationResult(
                    queueAdded = false,
                    onuAuthorized = false,
                    mikrotikError = ex.message,
                    oltError = ex.message
                )
            }
            applyInstallationResult(subscription, result, installationType)
            repository.save(subscription)
        }

        maybeRetryTr069(subscription, request)
        if (subscription.isProvisioningPending()) {
            scheduleNextAttempt(subscription)
            repository.save(subscription)
        }
        return subscription
    }

    private fun cpeProvisionEnabled(): Boolean =
        gatewayActivation.ifAvailable != null || cpeEnabled

    private fun mapOltStatus(result: InstallationResult): OltProvisionStatus = when {
        result.onuAuthorized -> OltProvisionStatus.COMPLETE
        !result.oltError.isNullOrBlank() -> OltProvisionStatus.FAILED
        else -> OltProvisionStatus.PENDING
    }

    private fun mapCpeStatus(subscription: Subscription, raw: String?) {
        val mapped = when (raw?.uppercase()) {
            "COMPLETE" -> Tr069ProvisionStatus.COMPLETE
            "PENDING" -> Tr069ProvisionStatus.PENDING
            "FAILED" -> Tr069ProvisionStatus.FAILED
            "NA" -> Tr069ProvisionStatus.NA
            else -> null
        } ?: return
        subscription.tr069ProvisionStatus = mapped
    }

    private fun maybeRetryTr069(subscription: Subscription, request: SubscriptionRequest) {
        if (!isTr069Eligible(subscription)) return
        if (subscription.oltProvisionStatus != OltProvisionStatus.COMPLETE) return
        if (subscription.tr069ProvisionStatus != Tr069ProvisionStatus.PENDING) return
        pullTr069FromGateway(subscription)
    }

    @Transactional
    fun refreshTr069FromGateway(subscription: Subscription): Subscription {
        if (!isTr069Eligible(subscription)) return subscription
        if (subscription.oltProvisionStatus != OltProvisionStatus.COMPLETE) return subscription
        val needsStatus = subscription.tr069ProvisionStatus == Tr069ProvisionStatus.PENDING ||
            subscription.tr069ProvisionStatus == Tr069ProvisionStatus.FAILED
        val needsDevice = subscription.tr069DeviceId.isNullOrBlank()
        if (!needsStatus && !needsDevice) {
            persistAcsLink(
                subscription,
                subscription.tr069DeviceId,
                subscription.tr069ProvisionStatus ?: Tr069ProvisionStatus.COMPLETE,
            )
            return subscription
        }
        val beforeStatus = subscription.tr069ProvisionStatus
        val beforeDevice = subscription.tr069DeviceId
        pullTr069FromGateway(subscription)
        if (subscription.tr069ProvisionStatus != beforeStatus ||
            subscription.tr069DeviceId != beforeDevice
        ) {
            repository.save(subscription)
        }
        return subscription
    }

    private fun pullTr069FromGateway(subscription: Subscription) {
        val sn = subscription.fiberOnuSn ?: return
        val gateway = gatewayActivation.ifAvailable ?: return
        try {
            val status = gateway.activationBySn(sn)
            mapCpeStatus(subscription, status.cpeStatus)
            status.message?.let { subscription.tr069LastError = it.take(500) }
            persistAcsLink(
                subscription,
                status.deviceId,
                subscription.tr069ProvisionStatus ?: Tr069ProvisionStatus.PENDING,
            )
        } catch (ex: Exception) {
            logger.warn("Fallo consulta estado CPE para suscripción ${subscription.id}", ex)
            persistProvisionError(ex)
        }
    }

    @Transactional
    fun reconcileDue(limit: Int = DEFAULT_BATCH_SIZE): Int {
        val page = org.springframework.data.domain.PageRequest.of(0, limit)
        val dueIds = repository.findDueForProvisionReconciliation(LocalDateTime.now(), page)
            .mapNotNull { it.id }
        if (dueIds.isEmpty()) return 0
        val due = repository.findAllWithProvisionRelationsByIdIn(dueIds)
        due.forEach { subscription ->
            try {
                val request = buildRequestFromSubscription(subscription)
                reconcile(subscription, request)
            } catch (ex: Exception) {
                logger.warn("No se pudo reconciliar suscripción ${subscription.id}", ex)
                persistProvisionError(ex)
                scheduleNextAttempt(subscription)
                repository.save(subscription)
            }
        }
        return due.size
    }

    fun buildRequestFromSubscription(subscription: Subscription): SubscriptionRequest {
        val onu = subscription.fiberOnuSn?.takeIf { it.isNotBlank() }?.let { sn ->
            OnuDto(sn = sn)
        }
        return SubscriptionRequest(
            firstName = subscription.firstName.orEmpty(),
            lastName = subscription.lastName.orEmpty(),
            dni = subscription.dni.orEmpty(),
            address = subscription.address.orEmpty(),
            phone = subscription.phone.orEmpty(),
            subscriptionDate = System.currentTimeMillis(),
            planId = subscription.plan?.id ?: 0,
            additionalDeviceIds = emptyList(),
            placeId = subscription.place?.id ?: 0,
            location = subscription.location
                ?: com.dscorp.wispadmin.wispadmin.data.model.GeoLocation(0.0, 0.0),
            technicianId = subscription.technician?.id ?: 0,
            hostDeviceId = subscription.hostDevice?.id ?: 0,
            napBoxId = subscription.napBox?.id,
            installationType = subscription.installationType ?: InstallationType.WIRELESS,
            equipmentCondition = subscription.equipmentCondition,
            clientRequestId = subscription.clientRequestId,
            clientIpAddress = subscription.ip,
            onu = onu,
            vlan = subscription.vlan,
            wifiSsid24 = subscription.wifiSsid24,
            wifiSsid5 = subscription.wifiSsid5,
        )
    }

    /**
     * Explicit TR-069 retry from the mobile app after MANUAL_REQUIRED (or PENDING).
     * COMPLETE is idempotent and does not re-run GenieACS.
     */
    fun retryTr069(subscriptionId: Int): com.dscorp.wispadmin.wispadmin.dto.SubscriptionDto {
        val subscription = repository.findById(subscriptionId)
            .orElseThrow { NoSuchElementException("Suscripción $subscriptionId no encontrada") }

        if (!isTr069Eligible(subscription)) {
            throw IllegalStateException("El reintento TR-069 solo aplica a instalaciones FIBER o TV con ONU")
        }
        if (subscription.oltProvisionStatus != OltProvisionStatus.COMPLETE) {
            throw IllegalStateException(
                "No se puede reintentar TR-069 hasta que la autorización OLT esté COMPLETE"
            )
        }

        val current = subscription.tr069ProvisionStatus
        if (current == Tr069ProvisionStatus.COMPLETE) {
            if (subscription.tr069DeviceId.isNullOrBlank()) {
                pullTr069FromGateway(subscription)
                if (!subscription.tr069DeviceId.isNullOrBlank()) {
                    repository.save(subscription)
                }
            }
            return subscription.toDto()
        }
        if (current != Tr069ProvisionStatus.MANUAL_REQUIRED &&
            current != Tr069ProvisionStatus.PENDING &&
            current != Tr069ProvisionStatus.FAILED
        ) {
            throw IllegalStateException(
                "TR-069 no admite reintento en estado ${current ?: "null"}"
            )
        }

        val sn = subscription.fiberOnuSn ?: throw IllegalStateException("ONU sin serial")
        val gateway = gatewayActivation.ifAvailable
            ?: throw IllegalStateException("Gateway ONU no disponible")
        val vlan = subscription.vlan?.trim()?.toIntOrNull() ?: 1
        val uniqueExternalId = runCatching {
            gateway.activationBySn(sn).uniqueExternalId?.trim()?.takeIf { it.isNotEmpty() }
        }.onFailure { ex ->
            logger.warn("No se pudo resolver uniqueExternalId de ONU {} antes del reintento TR-069: {}", sn, ex.message)
        }.getOrNull()
        val outcome = gateway.provision(
            com.dscorp.wispadmin.wispadmin.oltclient.GatewayCpeProvisionRequest(
                sn = sn,
                uniqueExternalId = uniqueExternalId,
                wanVlanId = vlan,
                ip = subscription.ip,
                ipSegment = subscription.ipPool?.ipSegment,
                pppoeUsername = subscription.pppoeUsername,
                pppoePassword = pppoeAccessService.decryptedPassword(subscription),
            )
        )
        mapCpeStatus(subscription, outcome.status)
        outcome.message?.let { subscription.tr069LastError = it.take(500) }
        persistAcsLink(
            subscription,
            outcome.deviceId,
            subscription.tr069ProvisionStatus ?: Tr069ProvisionStatus.PENDING,
        )
        repository.save(subscription)
        return subscription.toDto()
    }

    private fun persistAcsLink(
        subscription: Subscription,
        deviceId: String?,
        status: Tr069ProvisionStatus,
    ) {
        val id = deviceId?.takeIf { it.isNotBlank() } ?: return
        val previous = subscription.tr069DeviceId?.takeIf { it.isNotBlank() && it != id }
        subscription.tr069DeviceId = id
        val subscriptionId = subscription.id ?: return
        try {
            acsSyncService?.upsertFromProvision(
                subscriptionId = subscriptionId,
                outcome = Tr069ProvisionOutcome(status = status, deviceId = id),
                smartoltSerial = subscription.fiberOnuSn,
            )
        } catch (ex: Exception) {
            logger.warn("No se pudo upsert subscription_acs para {}: {}", subscriptionId, ex.message)
        }
        try {
            acsTagger?.apply(
                deviceId = id,
                subscriptionId = subscriptionId,
                kind = GenieAcsSubscriptionTags.serviceKind(
                    installationType = subscription.installationType,
                    planType = subscription.plan?.type,
                    planName = subscription.plan?.name,
                ),
                fullName = listOf(subscription.firstName, subscription.lastName)
                    .mapNotNull { it?.trim()?.takeIf { part -> part.isNotBlank() && part != "null" } }
                    .joinToString(" "),
                previousDeviceId = previous,
            )
        } catch (ex: Exception) {
            logger.warn("Fallo no bloqueante al etiquetar device {} de {}: {}", id, subscriptionId, ex.message)
        }
    }

    private fun isTr069Eligible(subscription: Subscription): Boolean {
        return when (subscription.installationType) {
            InstallationType.FIBER -> true
            InstallationType.ONLY_TV_FIBER -> !subscription.fiberOnuSn.isNullOrBlank()
            else -> false
        }
    }

    private fun persistProvisionError(ex: Exception) {
        try {
            errorLogRepository.save(ex.toErrorLog(Modules.SUBSCRIPTION))
        } catch (_: Exception) {
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 12
        const val DEFAULT_BATCH_SIZE = 50
    }
}
