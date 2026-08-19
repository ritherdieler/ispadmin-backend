package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.dscorp.wispadmin.wispadmin.cpe.AcsState
import com.dscorp.wispadmin.wispadmin.cpe.CpeCapabilities
import com.dscorp.wispadmin.wispadmin.cpe.CpeCapabilityResolver
import com.dscorp.wispadmin.wispadmin.cpe.CpeDeviceAdapter
import com.dscorp.wispadmin.wispadmin.cpe.CpeDeviceDescriptor
import com.dscorp.wispadmin.wispadmin.cpe.CpeWarnings
import com.dscorp.wispadmin.wispadmin.cpe.GponState
import com.dscorp.wispadmin.wispadmin.cpe.WanManagement
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.dto.CpeAcsStatusDto
import com.dscorp.wispadmin.wispadmin.dto.CpeCapabilitiesDto
import com.dscorp.wispadmin.wispadmin.dto.CpeGponStatusDto
import com.dscorp.wispadmin.wispadmin.dto.CpeNetworkConfigRequest
import com.dscorp.wispadmin.wispadmin.dto.CpeStatusDto
import com.dscorp.wispadmin.wispadmin.dto.CpeWifiConfigRequest
import com.dscorp.wispadmin.wispadmin.dto.UpdateCpeConfigRequest
import com.dscorp.wispadmin.wispadmin.dto.UpdateCpeConfigResponseDto
import com.dscorp.wispadmin.wispadmin.dto.UpdateWifiRequest
import com.dscorp.wispadmin.wispadmin.dto.UpdateWifiResponseDto
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant

/**
 * Orchestrates CPE provisioning across two channels: WAN through the OLT (OMCI) or the ACS
 * (TR-069) depending on the device capabilities, and Wi-Fi always through the ACS.
 */
@Service
class CpeManagementService(
    private val subscriptionRepository: SubscriptionRepository,
    private val oltMgrOnuRepository: OltMgrOnuRepository,
    private val cpeDeviceAdapter: CpeDeviceAdapter,
    private val oltService: OltService,
    private val capabilityResolver: CpeCapabilityResolver,
    private val cpeConfigValidator: CpeConfigValidator,
) {
    private val logger = LoggerFactory.getLogger(CpeManagementService::class.java)

    @Transactional(readOnly = true)
    fun getCpeStatus(subscriptionId: Int): CpeStatusDto {
        val subscription = requireSubscription(subscriptionId)
        val sn = subscription.fiberOnu?.sn?.takeIf { it.isNotBlank() }
            ?: return unknownStatus()

        val oltStatus = oltMgrOnuRepository.findBySnIgnoreCaseAndDeletedAtIsNull(sn)
            .orElse(null)
            ?.status
        val descriptor = describeQuietly(sn)
        val capabilities = capabilityResolver.resolve(descriptor?.toProfile())

        val gponState = when {
            oltStatus == null -> GponState.UNKNOWN
            oltStatus.runState.equals("online", ignoreCase = true) -> GponState.ONLINE
            else -> GponState.OFFLINE
        }

        return CpeStatusDto(
            online = gponState == GponState.ONLINE,
            rxDbm = formatDbm(oltStatus?.onuRxDbm),
            lastInform = descriptor?.lastInform,
            sn = sn,
            gponStatus = CpeGponStatusDto(
                state = gponState,
                rxDbm = formatDbm(oltStatus?.onuRxDbm),
                txDbm = formatDbm(oltStatus?.onuTxDbm),
            ),
            acsStatus = acsStatus(descriptor),
            capabilities = capabilities.toDto(descriptor),
        )
    }

    fun updateWifi(subscriptionId: Int, request: UpdateWifiRequest): UpdateWifiResponseDto {
        cpeConfigValidator.validate(
            UpdateCpeConfigRequest(
                wifi = CpeWifiConfigRequest(ssid = request.ssid, password = request.password)
            )
        )
        val sn = requireOnuSerial(requireSubscription(subscriptionId))
        cpeDeviceAdapter.applyConfiguration(
            serialNumber = sn,
            network = null,
            ssid = request.ssid.trim(),
            passphrase = request.password,
        )
        return UpdateWifiResponseDto(
            message = WIFI_QUEUED_MESSAGE,
            subscriptionId = subscriptionId,
        )
    }

    @Transactional
    fun updateCpeConfig(subscriptionId: Int, request: UpdateCpeConfigRequest): UpdateCpeConfigResponseDto {
        cpeConfigValidator.validate(request)
        val subscription = requireSubscription(subscriptionId)
        val sn = requireOnuSerial(subscription)
        val capabilities = capabilityResolver.resolve(describeQuietly(sn)?.toProfile())

        val warnings = mutableListOf<String>()
        val requestedNetwork = request.network
        val tr069Network = requestedNetwork?.takeIf { capabilities.canWriteWanViaTr069 }
        val omciNetwork = requestedNetwork
            ?.takeIf { !capabilities.canWriteWanViaTr069 && capabilities.canWriteWanViaOmci }
        if (requestedNetwork != null && tr069Network == null && omciNetwork == null) {
            warnings += CpeWarnings.WAN_NOT_MANAGEABLE
        }

        val wifi = request.wifi?.takeIf { capabilities.canWriteWifiViaTr069 }
        if (request.wifi != null && wifi == null) {
            warnings += CpeWarnings.WIFI_NOT_SUPPORTED
        }

        var appliedNetwork = false
        var networkChannel: WanManagement? = null

        if (omciNetwork != null) {
            val result = applyNetworkThroughOlt(subscription, sn, omciNetwork)
            warnings += result.warnings
            if (result.applied) {
                appliedNetwork = true
                networkChannel = WanManagement.OLT_OMCI
            }
        }

        var appliedWifi = false
        if (tr069Network != null || wifi != null) {
            val outcome = cpeDeviceAdapter.applyConfiguration(
                serialNumber = sn,
                network = tr069Network,
                ssid = wifi?.ssid?.trim(),
                passphrase = wifi?.password,
            )
            warnings += outcome.warnings
            if (outcome.appliedNetwork) {
                appliedNetwork = true
                networkChannel = WanManagement.TR069
            }
            appliedWifi = outcome.appliedWifi
        }

        if (!appliedNetwork && !appliedWifi) {
            logger.info("CPE {} did not accept any of the requested changes", sn)
        }

        return UpdateCpeConfigResponseDto(
            message = buildMessage(appliedNetwork, appliedWifi, networkChannel),
            subscriptionId = subscriptionId,
            appliedNetwork = appliedNetwork,
            appliedWifi = appliedWifi,
            networkChannel = networkChannel,
            warnings = warnings,
        )
    }

    private fun applyNetworkThroughOlt(
        subscription: Subscription,
        sn: String,
        network: CpeNetworkConfigRequest,
    ): OnuWanUpdateResult {
        val result = oltService.updateOnuWanConfig(
            sn = sn,
            vlan = network.vlanId,
            ip = network.ipAddress.trimToNull(),
            mask = network.subnetMask.trimToNull(),
            gateway = network.gateway.trimToNull(),
            dns1 = network.dnsPrimary.trimToNull(),
            dns2 = network.dnsSecondary.trimToNull(),
        )
        if (result.applied) persistNetwork(subscription, sn, network)
        return result
    }

    private fun persistNetwork(subscription: Subscription, sn: String, network: CpeNetworkConfigRequest) {
        network.ipAddress.trimToNull()?.let { ip ->
            if (subscription.ip != ip) {
                subscription.ip = ip
                try {
                    subscriptionRepository.save(subscription)
                } catch (e: DataIntegrityViolationException) {
                    throw IllegalArgumentException("La IP $ip ya está asignada a otra suscripción")
                }
            }
        }

        oltMgrOnuRepository.findBySnIgnoreCaseAndDeletedAtIsNull(sn).ifPresent { onu ->
            network.vlanId?.let { onu.mainVlanId = it }
            network.ipAddress.trimToNull()?.let { onu.ipAddress = it }
            network.subnetMask.trimToNull()?.let { onu.subnetMask = it }
            network.gateway.trimToNull()?.let { onu.defaultGateway = it }
            network.dnsPrimary.trimToNull()?.let { onu.dns1 = it }
            network.dnsSecondary.trimToNull()?.let { onu.dns2 = it }
            onu.updatedAt = Instant.now()
            oltMgrOnuRepository.save(onu)
        }
    }

    private fun describeQuietly(sn: String): CpeDeviceDescriptor? =
        runCatching { cpeDeviceAdapter.describe(sn) }
            .onFailure { error -> logger.warn("GenieACS lookup unavailable for SN {}: {}", sn, error.message) }
            .getOrNull()

    private fun acsStatus(descriptor: CpeDeviceDescriptor?): CpeAcsStatusDto {
        val lastInform = descriptor?.lastInform
        val state = when {
            lastInform == null -> AcsState.UNKNOWN
            descriptor.reachable -> AcsState.SYNCED
            else -> AcsState.STALE
        }
        return CpeAcsStatusDto(
            state = state,
            lastInform = lastInform,
            reachable = descriptor?.reachable == true,
        )
    }

    private fun unknownStatus(): CpeStatusDto = CpeStatusDto(
        online = false,
        rxDbm = null,
        lastInform = null,
        sn = null,
        gponStatus = CpeGponStatusDto(state = GponState.UNKNOWN, rxDbm = null, txDbm = null),
        acsStatus = CpeAcsStatusDto(state = AcsState.UNKNOWN, lastInform = null, reachable = false),
        capabilities = CpeCapabilities.FULL_TR069.toDto(null),
    )

    private fun CpeCapabilities.toDto(descriptor: CpeDeviceDescriptor?): CpeCapabilitiesDto =
        CpeCapabilitiesDto(
            canWriteWanViaTr069 = canWriteWanViaTr069,
            canWriteWanViaOmci = canWriteWanViaOmci,
            canWriteWifiViaTr069 = canWriteWifiViaTr069,
            wanManagedBy = wanManagedBy,
            vendor = descriptor?.vendor,
            model = descriptor?.model,
        )

    private fun buildMessage(
        appliedNetwork: Boolean,
        appliedWifi: Boolean,
        networkChannel: WanManagement?,
    ): String {
        val viaOlt = networkChannel == WanManagement.OLT_OMCI
        return when {
            appliedNetwork && appliedWifi && viaOlt ->
                "Se actualizó la red en la OLT y se encoló el cambio de Wi-Fi en GenieACS"
            appliedNetwork && appliedWifi -> "Se encolaron los cambios de red y Wi-Fi en GenieACS"
            appliedNetwork && viaOlt -> "Se actualizó la configuración de red en la OLT"
            appliedNetwork -> "Se encoló el cambio de red en GenieACS"
            appliedWifi -> WIFI_QUEUED_MESSAGE
            else -> "No se envió configuración al equipo; revise los avisos"
        }
    }

    private fun requireSubscription(subscriptionId: Int): Subscription =
        subscriptionRepository.findById(subscriptionId).orElseThrow {
            IllegalArgumentException("Suscripción no encontrada")
        }

    private fun requireOnuSerial(subscription: Subscription): String =
        subscription.fiberOnu?.sn?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("La suscripción no tiene ONU registrada")

    private fun formatDbm(value: BigDecimal?): String? =
        value?.stripTrailingZeros()?.toPlainString()

    private fun String?.trimToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        const val MIN_WIFI_PASSWORD_LENGTH = CpeConfigValidator.MIN_WIFI_PASSWORD_LENGTH
        private const val WIFI_QUEUED_MESSAGE = "Se encoló el cambio de Wi-Fi en GenieACS"
    }
}
