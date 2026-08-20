package com.dscorp.wispadmin.wispadmin.service.genieacs

import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.OltProvisionStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionDto
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import com.dscorp.wispadmin.wispadmin.service.subscription.strategies.FiberInstallationStrategy
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmSecretCipher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class Tr069PostInstallProvisioner(
    private val properties: GenieAcsProperties,
    private val provisioningService: Tr069ProvisioningService,
    private val repository: SubscriptionRepository,
    private val cipher: CrmSecretCipher,
    private val acsSyncService: SubscriptionAcsSyncService,
    private val fiberInstallationStrategy: FiberInstallationStrategy,
) {
    private val log = LoggerFactory.getLogger(Tr069PostInstallProvisioner::class.java)

    fun apply(dto: SubscriptionDto, request: SubscriptionRequest): SubscriptionDto {
        val subscriptionId = dto.id ?: return enrichDtoWithoutPersist(dto, request)

        return try {
            applyInternal(subscriptionId, dto, request)
        } catch (ex: Exception) {
            log.warn("TR-069 post-install falló para suscripción {}: {}", subscriptionId, ex.message)
            persistManualRequired(
                subscriptionId = subscriptionId,
                request = request,
                deviceId = null,
                error = ex.message?.take(500) ?: "Error TR-069 inesperado",
            )
        }
    }

    private fun applyInternal(
        subscriptionId: Int,
        dto: SubscriptionDto,
        request: SubscriptionRequest,
    ): SubscriptionDto {
        persistWifiFields(subscriptionId, request)

        if (!properties.enabled || request.installationType != InstallationType.FIBER) {
            return persistStatus(
                subscriptionId = subscriptionId,
                request = request,
                status = Tr069ProvisionStatus.NA,
                deviceId = null,
                error = null,
                messageOverride = null,
            )
        }

        if (dto.tr069ProvisionStatus == Tr069ProvisionStatus.COMPLETE) {
            return repository.findById(subscriptionId).map { it.toDto() }.orElse(dto)
        }

        val subscription = repository.findById(subscriptionId).orElse(null)
            ?: return enrichDtoWithoutPersist(dto, request)

        val oltStatus = subscription.oltProvisionStatus ?: dto.oltProvisionStatus
        if (oltStatus != OltProvisionStatus.COMPLETE) {
            return persistStatus(
                subscriptionId = subscriptionId,
                request = request,
                status = Tr069ProvisionStatus.PENDING,
                deviceId = null,
                error = null,
                messageOverride = "Esperando autorización OLT antes de GenieACS TR-069.",
            )
        }

        val waitTimeout = when {
            !request.clientRequestId.isNullOrBlank() && dto.alreadyRegistered ->
                properties.offlineWaitTimeoutMs
            !request.clientRequestId.isNullOrBlank() &&
                subscription.tr069ProvisionStatus == Tr069ProvisionStatus.PENDING &&
                dto.alreadyRegistered ->
                properties.offlineWaitTimeoutMs
            dto.alreadyRegistered -> properties.offlineWaitTimeoutMs
            else -> properties.waitTimeoutMs
        }

        if (request.vlan.isNullOrBlank() && !subscription.vlan.isNullOrBlank()) {
            request.vlan = subscription.vlan
        }
        val wanVlanId = fiberInstallationStrategy.resolveVlan(
            subscription.apply {
                if (vlan.isNullOrBlank()) {
                    vlan = request.vlan
                }
            }
        ).toInt()

        val smartoltSerial = request.onu?.sn ?: subscription.fiberOnu?.sn
        val outcome = provisioningService.provision(
            Tr069ProvisionRequest(
                onuSerial = smartoltSerial,
                onuTypeName = request.onu?.onu_type_name ?: subscription.fiberOnu?.onu_type_name,
                ip = subscription.ip ?: request.clientIpAddress,
                ipSegment = subscription.ipPool?.ipSegment,
                wifiSsid24 = request.wifiSsid24 ?: subscription.wifiSsid24,
                wifiPassword24 = resolvePassword(request.wifiPassword24, subscription.wifiPassword24Enc),
                wifiSsid5 = request.wifiSsid5 ?: subscription.wifiSsid5,
                wifiPassword5 = resolvePassword(request.wifiPassword5, subscription.wifiPassword5Enc),
                waitTimeoutMs = waitTimeout,
                wanVlanId = wanVlanId,
            )
        )

        val enriched = persistStatus(
            subscriptionId = subscriptionId,
            request = request,
            status = outcome.status,
            deviceId = outcome.deviceId,
            error = outcome.error,
            messageOverride = outcome.message,
        )
        syncAcsSnapshot(subscriptionId, outcome, smartoltSerial)
        return enriched
    }

    private fun syncAcsSnapshot(
        subscriptionId: Int,
        outcome: Tr069ProvisionOutcome,
        smartoltSerial: String?,
    ) {
        try {
            acsSyncService.upsertFromProvision(
                subscriptionId = subscriptionId,
                outcome = outcome,
                smartoltSerial = smartoltSerial,
            )
        } catch (ex: Exception) {
            log.warn(
                "Fallo no bloqueante al sincronizar subscription_acs para {}: {}",
                subscriptionId,
                ex.message,
            )
        }
    }

    @Transactional
    fun persistWifiFields(subscriptionId: Int, request: SubscriptionRequest) {
        val subscription = repository.findById(subscriptionId).orElse(null) ?: return
        applyWifiFromRequest(subscription, request)
        repository.save(subscription)
    }

    @Transactional
    fun persistStatus(
        subscriptionId: Int,
        request: SubscriptionRequest,
        status: Tr069ProvisionStatus,
        deviceId: String?,
        error: String?,
        messageOverride: String?,
    ): SubscriptionDto {
        val subscription = repository.findById(subscriptionId).orElse(null)
            ?: return enrichDtoWithoutPersist(
                SubscriptionDto(id = subscriptionId),
                request,
            ).copy(
                tr069ProvisionStatus = status,
                tr069RequiresManualConfig = status == Tr069ProvisionStatus.MANUAL_REQUIRED,
                tr069Message = messageOverride ?: error,
                wifiSsid24 = request.wifiSsid24,
                wifiSsid5 = request.wifiSsid5,
            )
        applyWifiFromRequest(subscription, request)
        subscription.tr069ProvisionStatus = status
        subscription.tr069DeviceId = deviceId ?: subscription.tr069DeviceId
        subscription.tr069LastError = when (status) {
            Tr069ProvisionStatus.COMPLETE, Tr069ProvisionStatus.NA -> null
            else -> (error ?: messageOverride)?.take(500)
        }
        if (status == Tr069ProvisionStatus.COMPLETE && !subscription.isMikrotikOrOltPending()) {
            subscription.provisionNextAttemptAt = null
            subscription.provisionLastError = null
        } else if (
            status == Tr069ProvisionStatus.PENDING &&
            subscription.oltProvisionStatus == OltProvisionStatus.COMPLETE &&
            subscription.provisionNextAttemptAt == null
        ) {
            subscription.provisionNextAttemptAt = java.time.LocalDateTime.now().plusMinutes(5)
        }
        return repository.save(subscription).toDto().let { saved ->
            when {
                messageOverride != null && status == Tr069ProvisionStatus.COMPLETE ->
                    saved.copy(tr069Message = messageOverride)
                messageOverride != null &&
                    (status == Tr069ProvisionStatus.MANUAL_REQUIRED ||
                        status == Tr069ProvisionStatus.PENDING) ->
                    saved.copy(tr069Message = error?.takeIf { it.isNotBlank() } ?: messageOverride)
                else -> saved
            }
        }
    }

    private fun persistManualRequired(
        subscriptionId: Int,
        request: SubscriptionRequest,
        deviceId: String?,
        error: String,
    ): SubscriptionDto = persistStatus(
        subscriptionId = subscriptionId,
        request = request,
        status = Tr069ProvisionStatus.MANUAL_REQUIRED,
        deviceId = deviceId,
        error = error,
        messageOverride = error,
    )

    private fun applyWifiFromRequest(subscription: Subscription, request: SubscriptionRequest) {
        request.wifiSsid24?.let { subscription.wifiSsid24 = it }
        request.wifiSsid5?.let { subscription.wifiSsid5 = it }
        request.wifiPassword24?.takeIf { it.isNotBlank() }?.let {
            subscription.wifiPassword24Enc = cipher.encrypt(it)
        }
        request.wifiPassword5?.takeIf { it.isNotBlank() }?.let {
            subscription.wifiPassword5Enc = cipher.encrypt(it)
        }
    }

    private fun resolvePassword(plain: String?, encrypted: String?): String? {
        if (!plain.isNullOrBlank()) return plain
        if (encrypted.isNullOrBlank()) return null
        return try {
            cipher.decrypt(encrypted)
        } catch (_: Exception) {
            null
        }
    }

    private fun enrichDtoWithoutPersist(dto: SubscriptionDto, request: SubscriptionRequest): SubscriptionDto {
        val status = when {
            !properties.enabled -> Tr069ProvisionStatus.NA
            request.installationType != InstallationType.FIBER -> Tr069ProvisionStatus.NA
            else -> dto.tr069ProvisionStatus ?: Tr069ProvisionStatus.PENDING
        }
        return dto.copy(
            tr069ProvisionStatus = status,
            tr069RequiresManualConfig = status == Tr069ProvisionStatus.MANUAL_REQUIRED,
            wifiSsid24 = request.wifiSsid24 ?: dto.wifiSsid24,
            wifiSsid5 = request.wifiSsid5 ?: dto.wifiSsid5,
        )
    }
}
