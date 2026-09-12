package com.dscorp.wispadmin.wispadmin.service.mikrotik

import com.dscorp.wispadmin.routeros.port.MikrotikException
import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.dscorp.wispadmin.wispadmin.data.model.AccessMode
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.PppoeProvisionStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.usesPppoe
import com.dscorp.wispadmin.wispadmin.extensions.NetworkDeviceConnectionManager
import com.dscorp.wispadmin.wispadmin.extensions.executeCommand
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmSecretCipher
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

typealias DeviceSessionRunner = (NetworkDevice, (MikrotikSession) -> Unit) -> Unit

@Service
class PppoeAccessService(
    private val pppoeManager: PppoeManagerService,
    private val secretCipher: CrmSecretCipher,
    private val mockMode: () -> Boolean = { false },
    private val sessionRunner: DeviceSessionRunner
) {
    @Autowired
    constructor(
        pppoeManager: PppoeManagerService,
        secretCipher: CrmSecretCipher
    ) : this(
        pppoeManager,
        secretCipher,
        { NetworkDeviceConnectionManager.isMikroTikMockModeEnabled() },
        { device, block -> device.executeCommand(block) }
    )

    private val logger = LoggerFactory.getLogger(PppoeAccessService::class.java)

    fun ensureSecret(subscription: Subscription, device: NetworkDevice): PppoeSecretResult {
        if (!isDynamic(subscription)) return PppoeSecretResult()
        if (mockMode()) return PppoeSecretResult(created = true)

        val password = subscription.pppoePasswordEnc
            ?.takeIf { secretCipher.looksEncrypted(it) }
            ?.let { secretCipher.decrypt(it) }

        var result = PppoeSecretResult(error = ERROR_NO_SESSION)
        return try {
            sessionRunner(device) { session ->
                result = pppoeManager.ensureSecret(session, subscription, password)
            }
            applyOutcome(subscription, result)
        } catch (error: MikrotikException) {
            logger.error("No se pudo crear el secret PPPoE de la suscripción ${subscription.id}", error)
            applyOutcome(subscription, PppoeSecretResult(error = error.message))
        }
    }

    fun cut(subscription: Subscription, device: NetworkDevice): Boolean {
        val username = pppoeUsername(subscription) ?: return false
        val applied = switchProfile(device, username, PppoeProfileCatalog.CUT_PROFILE, kick = true)
        if (applied) {
            subscription.pppoeProvisionStatus = PppoeProvisionStatus.CUT
        }
        return applied
    }

    fun restore(subscription: Subscription, device: NetworkDevice): Boolean {
        val username = pppoeUsername(subscription) ?: return false
        val profile = PppoeProfileCatalog.profileName(subscription.plan) ?: return false
        val applied = switchProfile(device, username, profile, kick = true)
        if (applied) {
            subscription.pppoeProfile = profile
            subscription.pppoeProvisionStatus = PppoeProvisionStatus.SECRET_CREATED
        }
        return applied
    }

    fun applyPlanProfile(subscription: Subscription, device: NetworkDevice): Boolean {
        if (subscription.pppoeProvisionStatus == PppoeProvisionStatus.CUT) return false
        val username = pppoeUsername(subscription) ?: return false
        val profile = PppoeProfileCatalog.profileName(subscription.plan) ?: return false
        val applied = switchProfile(device, username, profile, kick = false)
        if (applied) {
            subscription.pppoeProfile = profile
        }
        return applied
    }

    private fun switchProfile(
        device: NetworkDevice,
        username: String,
        profile: String,
        kick: Boolean
    ): Boolean {
        if (mockMode()) return true

        var applied = false
        return try {
            sessionRunner(device) { session ->
                applied = pppoeManager.applyProfile(session, username, profile)
                if (applied && kick) {
                    pppoeManager.kickSession(session, username)
                }
            }
            applied
        } catch (error: MikrotikException) {
            logger.error("No se pudo aplicar el perfil $profile al usuario PPPoE $username", error)
            false
        }
    }

    private fun applyOutcome(subscription: Subscription, result: PppoeSecretResult): PppoeSecretResult {
        if (result.successful) {
            result.profile?.let { subscription.pppoeProfile = it }
            subscription.pppoeProvisionStatus = PppoeProvisionStatus.SECRET_CREATED
        } else {
            subscription.pppoeProvisionStatus = PppoeProvisionStatus.FAILED
        }
        return result
    }

    fun decryptedPassword(subscription: Subscription): String? {
        if (!isDynamic(subscription)) return null
        return subscription.pppoePasswordEnc
            ?.takeIf { secretCipher.looksEncrypted(it) }
            ?.let { secretCipher.decrypt(it) }
    }

    private fun isDynamic(subscription: Subscription) =
        subscription.accessMode == AccessMode.PPPOE_DYNAMIC

    private fun pppoeUsername(subscription: Subscription): String? {
        if (!subscription.accessMode.usesPppoe()) return null
        return subscription.pppoeUsername?.trim()?.takeIf { it.isNotEmpty() }
    }

    companion object {
        const val ERROR_NO_SESSION = "No se pudo abrir sesión con MikroTik"
    }
}
