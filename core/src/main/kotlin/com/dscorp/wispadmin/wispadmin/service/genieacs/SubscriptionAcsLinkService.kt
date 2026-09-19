package com.dscorp.wispadmin.wispadmin.service.genieacs

import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus
import com.dscorp.wispadmin.wispadmin.dto.AcsGhostDevice
import com.dscorp.wispadmin.wispadmin.dto.AcsLinkStatus
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionAcsLinkResult
import com.dscorp.wispadmin.wispadmin.oltclient.OltGatewayHttpClient
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.search.application.SubscriptionChangedEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime

@Service
class SubscriptionAcsLinkService(
    private val subscriptionRepository: SubscriptionRepository,
    private val client: GenieAcsClient,
    private val syncService: SubscriptionAcsSyncService,
    private val tagger: GenieAcsSubscriptionTagger,
    private val gatewayHttp: ObjectProvider<OltGatewayHttpClient>,
    private val eventPublisher: ApplicationEventPublisher,
) {
    var clock: Clock = Clock.systemUTC()
    private val objectMapper = ObjectMapper()
    private val logger = LoggerFactory.getLogger(SubscriptionAcsLinkService::class.java)

    @Transactional
    fun link(deviceId: String, dryRun: Boolean = false): SubscriptionAcsLinkResult {
        val id = deviceId.trim()
        val device = client.findDeviceById(id)
            ?: return SubscriptionAcsLinkResult(
                status = AcsLinkStatus.SKIP_DEVICE_NOT_FOUND,
                deviceId = id,
            )
        val suffix = suffixOf(device)
            ?: return SubscriptionAcsLinkResult(
                status = AcsLinkStatus.SKIP_DEVICE_NOT_FOUND,
                deviceId = id,
                message = "serial sin sufijo hex",
            )
        val holders = subscriptionRepository.findByOnuSerialOrSuffix(
            device.serialNumber ?: device.id,
            suffix,
        )
        val occupied = holders.filter { it.serviceStatus != ServiceStatus.CANCELLED }
        when {
            occupied.size > 1 -> return SubscriptionAcsLinkResult(
                status = AcsLinkStatus.SKIP_AMBIGUOUS,
                deviceId = id,
                message = occupied.joinToString(",") { "#${it.id}" },
            )
            occupied.isEmpty() && holders.isNotEmpty() -> return SubscriptionAcsLinkResult(
                status = AcsLinkStatus.SKIP_NOT_ACTIVE,
                deviceId = id,
            )
            occupied.isEmpty() -> return SubscriptionAcsLinkResult(
                status = AcsLinkStatus.SKIP_NONE,
                deviceId = id,
            )
        }
        val subscription = occupied.single()
        val subscriptionId = subscription.id
            ?: return SubscriptionAcsLinkResult(status = AcsLinkStatus.SKIP_NONE, deviceId = id)
        if (dryRun) {
            return SubscriptionAcsLinkResult(
                status = AcsLinkStatus.LINKED,
                deviceId = id,
                subscriptionId = subscriptionId,
            )
        }
        persistLink(subscription, device, suffix)
        return SubscriptionAcsLinkResult(
            status = AcsLinkStatus.LINKED,
            deviceId = id,
            subscriptionId = subscriptionId,
        )
    }

    fun listGhosts(): List<AcsGhostDevice> {
        return client.listDevices().mapNotNull { device ->
            val suffix = suffixOf(device) ?: return@mapNotNull null
            val holders = subscriptionRepository.findByOnuSerialOrSuffix(
                device.serialNumber ?: device.id,
                suffix,
            )
            val occupied = holders.filter { it.serviceStatus != ServiceStatus.CANCELLED }
            if (occupied.isNotEmpty()) return@mapNotNull null
            AcsGhostDevice(
                deviceId = device.id,
                serialNumber = device.serialNumber,
                lastInform = device.lastInform,
                suffix = suffix,
            )
        }
    }

    fun deleteGhost(deviceId: String): Boolean {
        val id = deviceId.trim()
        val device = client.findDeviceById(id)
            ?: throw NoSuchElementException("Device $id no está en GenieACS")
        val suffix = suffixOf(device)
            ?: throw IllegalStateException("Device $id no tiene sufijo hex")
        val occupied = subscriptionRepository.findByOnuSerialOrSuffix(
            device.serialNumber ?: device.id,
            suffix,
        ).filter { it.serviceStatus != ServiceStatus.CANCELLED }
        if (occupied.isNotEmpty()) {
            val detail = occupied.joinToString(", ") { "#${it.id}" }
            throw IllegalStateException("El CPE $id tiene dueño $detail")
        }
        return client.deleteDevice(id)
    }

    private fun persistLink(subscription: Subscription, device: GenieAcsDevice, suffix: String) {
        val previous = subscription.tr069DeviceId?.takeIf { it.isNotBlank() && it != device.id }
        subscription.tr069DeviceId = device.id
        val gatewaySn = lookupGatewaySerial(subscription.fiberOnuSn, suffix)
        if (!gatewaySn.isNullOrBlank()) {
            subscription.fiberOnuSn = gatewaySn
        }
        val fresh = isFreshInform(device.lastInform)
        if (fresh) {
            subscription.tr069ProvisionStatus = Tr069ProvisionStatus.COMPLETE
        }
        val snapshotStatus = if (fresh) {
            Tr069ProvisionStatus.COMPLETE
        } else {
            subscription.tr069ProvisionStatus ?: Tr069ProvisionStatus.PENDING
        }
        val subscriptionId = subscription.id ?: return
        subscriptionRepository.save(subscription)
        eventPublisher.publishEvent(SubscriptionChangedEvent(subscriptionId))
        val serial = subscription.fiberOnuSn
        try {
            syncService.upsertFromProvision(
                subscriptionId = subscriptionId,
                outcome = Tr069ProvisionOutcome(
                    status = snapshotStatus,
                    deviceId = device.id,
                    acsSnapshot = Tr069AcsSnapshot(
                        serialSuffix = suffix,
                        lastInformAt = GenieAcsValues.parseDateTime(device.lastInform),
                        productClass = device.productClass,
                        oui = device.oui,
                        manufacturer = device.manufacturer,
                        connectionRequestUrl = device.connectionRequestUrl,
                        softwareVersion = device.softwareVersion,
                        hardwareVersion = device.hardwareVersion,
                        lastBootAt = GenieAcsValues.parseDateTime(device.lastBoot),
                        wanIpCache = subscription.ip,
                        ssid24 = device.ssid24,
                        ssid5 = device.ssid5,
                    ),
                ),
                smartoltSerial = serial,
            )
        } catch (ex: Exception) {
            logger.warn("No se pudo upsert subscription_acs para {}: {}", subscriptionId, ex.message)
        }
        try {
            tagger.apply(
                deviceId = device.id,
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
            logger.warn("Fallo no bloqueante al etiquetar device {} de {}: {}", device.id, subscriptionId, ex.message)
        }
        ensureMgmt(serial)
    }

    private fun ensureMgmt(sn: String?) {
        if (sn.isNullOrBlank()) return
        val http = gatewayHttp.ifAvailable ?: return
        try {
            http.postJsonBody("/api/olt-gateway/onus/$sn/service-port/ensure-mgmt", """{"vlan":1000}""")
        } catch (ex: Exception) {
            logger.warn("ensure-mgmt VLAN 1000 falló para {}: {}", sn, ex.message)
        }
    }

    private fun lookupGatewaySerial(current: String?, suffix: String): String? {
        val http = gatewayHttp.ifAvailable ?: return current
        val candidates = listOfNotNull(
            current?.takeIf { it.isNotBlank() },
            "VSOL00$suffix",
            "HWTC15$suffix",
            "HWTC00$suffix",
        ).distinct()
        for (sn in candidates) {
            val body = try {
                http.getJson("/api/olt-gateway/onu/get_onus_details_by_sn/$sn").body
            } catch (_: Exception) {
                continue
            } ?: continue
            val found = try {
                objectMapper.readTree(body).path("onus").firstOrNull()?.path("sn")?.asText()
            } catch (_: Exception) {
                null
            }
            if (!found.isNullOrBlank()) return found
        }
        return current
    }

    private fun isFreshInform(raw: String?): Boolean {
        val lastInform = GenieAcsValues.parseDateTime(raw) ?: return false
        val cutoff = LocalDateTime.now(clock).minus(INFORM_FRESHNESS)
        return lastInform.isAfter(cutoff)
    }

    private fun suffixOf(device: GenieAcsDevice): String? {
        return Tr069SerialMatcher.normalizeSuffix(device.serialNumber)
            ?: Tr069SerialMatcher.normalizeSuffix(device.id)
    }

    companion object {
        private val INFORM_FRESHNESS: Duration = Duration.ofHours(24)
    }
}
