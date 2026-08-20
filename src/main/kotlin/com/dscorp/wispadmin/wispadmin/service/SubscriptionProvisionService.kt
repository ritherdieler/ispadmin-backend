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
import com.dscorp.wispadmin.wispadmin.service.genieacs.GenieAcsProperties
import com.dscorp.wispadmin.wispadmin.service.subscription.strategies.InstallationResult
import com.dscorp.wispadmin.wispadmin.service.subscription.strategies.InstallationStrategyFactory
import org.slf4j.LoggerFactory
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
    private val genieAcsProperties: GenieAcsProperties,
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
                    if (genieAcsProperties.enabled) Tr069ProvisionStatus.PENDING
                    else Tr069ProvisionStatus.NA
            }
            InstallationType.ONLY_TV_FIBER -> {
                subscription.mikrotikProvisionStatus = MikrotikProvisionStatus.COMPLETE
                subscription.oltProvisionStatus = OltProvisionStatus.NA
                subscription.tr069ProvisionStatus = Tr069ProvisionStatus.NA
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
                subscription.oltProvisionStatus =
                    if (result.onuAuthorized) OltProvisionStatus.COMPLETE
                    else OltProvisionStatus.PENDING
            }
            InstallationType.ONLY_TV_FIBER -> {
                subscription.mikrotikProvisionStatus = MikrotikProvisionStatus.COMPLETE
                subscription.oltProvisionStatus = OltProvisionStatus.NA
            }
        }

        val errors = listOfNotNull(result.mikrotikError, result.oltError, thrownError)
            .joinToString(" | ")
            .ifBlank { null }
        if (errors != null) {
            subscription.provisionLastError = errors.take(500)
        } else if (!subscription.isProvisioningPending()) {
            subscription.provisionLastError = null
            subscription.provisionNextAttemptAt = null
        }

        if (subscription.isProvisioningPending()) {
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
        if (!subscription.isProvisioningPending()) {
            return subscription
        }
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
        return repository.save(subscription)
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
        val onu = subscription.fiberOnu?.let {
            OnuDto(
                sn = it.sn,
                olt_id = it.olt_id,
                pon_type = it.pon_type,
                board = it.board,
                port = it.port,
                onu = it.onu,
                onu_type_id = it.onu_type_id,
                onu_type_name = it.onu_type_name
            )
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
            onu = onu
        )
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
