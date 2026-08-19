package com.dscorp.wispadmin.wispadmin.cpe

import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * V-SOL ONUs (V2804AX15T and siblings) expose Wi-Fi over CWMP but keep the WAN profile
 * under OMCI control, so the ACS never sees writable WAN or VLAN nodes.
 */
@Component
@Order(0)
class VsolCpeCapabilityStrategy : CpeCapabilityStrategy {

    override fun supports(profile: CpeDeviceProfile): Boolean {
        val vendor = normalize(profile.vendor)
        val model = normalize(profile.model)
        if (vendor.contains(VSOL_NAME) || model.contains(VSOL_NAME)) return true
        if (vendor in VSOL_OUIS) return true
        return MODEL_PATTERN.matches(model)
    }

    override fun capabilities(profile: CpeDeviceProfile): CpeCapabilities = CpeCapabilities.OMCI_MANAGED_WAN

    private fun normalize(value: String?): String =
        value?.uppercase()?.filter { it.isLetterOrDigit() }.orEmpty()

    private companion object {
        const val VSOL_NAME = "VSOL"
        val VSOL_OUIS = setOf("B46415")
        val MODEL_PATTERN = Regex("^V\\d{3,4}[A-Z0-9]*$")
    }
}
