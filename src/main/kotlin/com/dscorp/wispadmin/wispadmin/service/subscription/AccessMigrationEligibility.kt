package com.dscorp.wispadmin.wispadmin.service.subscription

import com.dscorp.wispadmin.wispadmin.data.model.AccessMode
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.service.mikrotik.PppoeProfileCatalog
import com.dscorp.wispadmin.wispadmin.util.isValidIpAddress
import java.net.URI

data class AccessMigrationCpeModel(
    val productClass: String?,
    val hasPppPath: Boolean,
)

data class AccessMigrationAcsState(
    val connectionRequestUrl: String?,
    val lastInformAt: String? = null,
    val wanIpPath: String? = null,
    val wanPppPath: String? = null,
)

sealed class AccessMigrationEligibilityResult {
    data object Eligible : AccessMigrationEligibilityResult()
    data class Ineligible(val reason: String) : AccessMigrationEligibilityResult()

    val eligible: Boolean get() = this is Eligible
}

object AccessMigrationEligibility {

    fun evaluate(
        subscription: Subscription,
        plan: Plan?,
        cpeModel: AccessMigrationCpeModel,
        acs: AccessMigrationAcsState,
    ): AccessMigrationEligibilityResult {
        if (subscription.installationType != InstallationType.FIBER) {
            return AccessMigrationEligibilityResult.Ineligible("Solo FIBER es migrable")
        }
        if (subscription.accessMode != AccessMode.STATIC_IP) {
            return AccessMigrationEligibilityResult.Ineligible("La suscripción no está en IP estática")
        }
        if (subscription.fiberOnuSn.isNullOrBlank()) {
            return AccessMigrationEligibilityResult.Ineligible("La suscripción no tiene serial de ONU")
        }
        if (subscription.ip.isValidIpAddress().not()) {
            return AccessMigrationEligibilityResult.Ineligible("La IP de servicio no es válida")
        }
        if (PppoeProfileCatalog.profileName(plan) == null) {
            return AccessMigrationEligibilityResult.Ineligible("El plan no tiene velocidades para derivar un perfil PPPoE")
        }
        if (!cpeModel.hasPppPath) {
            return AccessMigrationEligibilityResult.Ineligible("El modelo ${cpeModel.productClass ?: "desconocido"} no declara ruta WANPPP")
        }
        val host = managementHost(acs.connectionRequestUrl)
            ?: return AccessMigrationEligibilityResult.Ineligible("GenieACS no reporta ConnectionRequestURL")
        if (host == subscription.ip?.trim()) {
            return AccessMigrationEligibilityResult.Ineligible("El CPE se gestiona por la IP de servicio")
        }
        if (!isDedicatedManagement(host)) {
            return AccessMigrationEligibilityResult.Ineligible("La gestión no está en provisioning-255")
        }
        return AccessMigrationEligibilityResult.Eligible
    }

    fun managementHost(url: String?): String? {
        val raw = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            val withScheme = if (raw.contains("://")) raw else "http://$raw"
            URI(withScheme).host?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            IPV4.find(raw)?.groupValues?.get(1)
        }
    }

    fun isDedicatedManagement(host: String): Boolean =
        isProvisioning255(host) || isLabAcsMgmt(host)

    fun isProvisioning255(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        if (parts[0] != "192" || parts[1] != "168") return false
        val third = parts[2].toIntOrNull() ?: return false
        return third in 252..255
    }

    fun isLabAcsMgmt(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        if (parts[0] != "10" || parts[1] != "20") return false
        val third = parts[2].toIntOrNull() ?: return false
        return third in 0..3
    }

    private val IPV4 = Regex("""(\d{1,3}(?:\.\d{1,3}){3})""")
}

object AccessMigrationWanGuard {
    fun replacedClientWanIpPath(wanIpPath: String?, pppPath: String?): String? {
        val staticWan = wanIpPath?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val ppp = pppPath?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val wcdInstance = ppp.substringBefore(".WANIPConnection").substringBefore(".WANPPPConnection")
        if (wcdInstance.isBlank() || wcdInstance == ppp) return null
        return staticWan.takeIf { it.startsWith("$wcdInstance.") }
    }

    fun wouldLeaveTwoActiveWans(wanIpPath: String?, pppPath: String?): Boolean {
        if (wanIpPath.isNullOrBlank() && pppPath.isNullOrBlank()) return false
        if (pppPath.isNullOrBlank()) return true
        return replacedClientWanIpPath(wanIpPath, pppPath) == null
    }
}
