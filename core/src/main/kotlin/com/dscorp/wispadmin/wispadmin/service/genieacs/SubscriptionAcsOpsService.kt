package com.dscorp.wispadmin.wispadmin.service.genieacs

import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.SubscriptionAcs
import com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionAcsDto
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionAcsRebootResultDto
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionAcsRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class SubscriptionAcsOpsService(
    private val subscriptionRepository: SubscriptionRepository,
    private val acsRepository: SubscriptionAcsRepository,
    private val client: GenieAcsClient,
    private val syncService: SubscriptionAcsSyncService,
) {
    private val log = LoggerFactory.getLogger(SubscriptionAcsOpsService::class.java)

    fun getAcs(subscriptionId: Int): SubscriptionAcsDto {
        return acsRepository.findById(subscriptionId)
            .orElseThrow { NoSuchElementException("No hay snapshot ACS para la suscripción $subscriptionId") }
            .toDto()
    }

    @Transactional
    fun refresh(subscriptionId: Int): SubscriptionAcsDto {
        val subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow { NoSuchElementException("Suscripción $subscriptionId no encontrada") }
        val existing = acsRepository.findById(subscriptionId).orElse(null)

        val device = resolveDevice(subscription, existing)
            ?: throw IllegalStateException(
                "No se encontró el CPE en GenieACS para la suscripción $subscriptionId"
            )

        val status = existing?.provisionStatus ?: Tr069ProvisionStatus.PENDING

        val snapshot = Tr069AcsSnapshot(
            serialSuffix = existing?.serialSuffix
                ?: Tr069SerialMatcher.normalizeSuffix(subscription.fiberOnuSn)
                ?: Tr069SerialMatcher.normalizeSuffix(device.serialNumber),
            lastInformAt = Tr069ProvisioningService.parseGenieAcsDateTime(device.lastInform),
            productClass = device.productClass,
            oui = device.oui,
            manufacturer = device.manufacturer,
            connectionRequestUrl = device.connectionRequestUrl,
            softwareVersion = device.softwareVersion,
            hardwareVersion = device.hardwareVersion,
            lastBootAt = Tr069ProvisioningService.parseGenieAcsDateTime(device.lastBoot),
            wanIpCache = subscription.ip ?: existing?.wanIpCache,
            ssid24 = existing?.ssid24,
            ssid5 = existing?.ssid5,
            lastTaskId = existing?.lastTaskId,
            lastTaskStatus = existing?.lastTaskStatus,
            lastTaskAt = existing?.lastTaskAt,
        )

        syncService.upsertFromProvision(
            subscriptionId = subscriptionId,
            outcome = Tr069ProvisionOutcome(
                status = status,
                deviceId = device.id,
                error = if (status == Tr069ProvisionStatus.MANUAL_REQUIRED) {
                    existing?.lastError
                } else null,
                acsSnapshot = snapshot,
            ),
            smartoltSerial = subscription.fiberOnuSn ?: existing?.smartoltSerial,
        )

        return getAcs(subscriptionId)
    }

    @Transactional
    fun reboot(subscriptionId: Int): SubscriptionAcsRebootResultDto {
        val subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow { NoSuchElementException("Suscripción $subscriptionId no encontrada") }
        val existing = acsRepository.findById(subscriptionId).orElse(null)
        val deviceId = existing?.genieacsDeviceId
            ?: throw IllegalStateException(
                "La suscripción $subscriptionId no tiene deviceId GenieACS para reiniciar"
            )

        var result = client.reboot(deviceId, connectionRequest = true)
        if (result.connectionRequestFailed) {
            log.warn("CR falló al reiniciar {}; reintentando sin connection_request", deviceId)
            result = client.reboot(deviceId, connectionRequest = false)
        }

        if (existing != null) {
            existing.lastTaskId = result.taskId ?: existing.lastTaskId
            existing.lastTaskStatus = when {
                result.connectionRequestFailed -> "cr_failed"
                result.accepted -> "accepted"
                else -> "rejected"
            }
            existing.lastTaskAt = LocalDateTime.now(ZoneOffset.UTC)
            existing.updatedAt = LocalDateTime.now()
            acsRepository.save(existing)
        }

        if (!result.accepted) {
            throw IllegalStateException(
                result.body?.take(200) ?: "GenieACS rechazó el reboot de $deviceId"
            )
        }

        return SubscriptionAcsRebootResultDto(
            subscriptionId = subscriptionId,
            deviceId = deviceId,
            taskId = result.taskId,
            accepted = result.accepted,
            message = if (result.connectionRequestFailed) {
                "Reinicio encolado para el próximo Inform (Connection Request no disponible)"
            } else {
                "Reinicio ONU enviado vía TR-069"
            },
        )
    }

    private fun resolveDevice(
        subscription: Subscription,
        existing: SubscriptionAcs?,
    ): GenieAcsDevice? {
        val devices = client.listDevices()
        val deviceId = existing?.genieacsDeviceId
        if (!deviceId.isNullOrBlank()) {
            devices.firstOrNull { it.id == deviceId }?.let { return it }
        }
        val sn = subscription.fiberOnuSn ?: existing?.smartoltSerial
        return when (val match = Tr069SerialMatcher.findUnique(sn, devices)) {
            is Tr069SerialMatch.Found -> match.device
            else -> null
        }
    }
}
