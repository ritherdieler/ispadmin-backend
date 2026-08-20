package com.dscorp.wispadmin.wispadmin.service.genieacs

data class Tr069ParameterValue(
    val path: String,
    val value: String,
    val type: String,
)

data class Tr069ModelProfile(
    val productClass: String,
    val wanIpConnectionPath: String,
    val wanGponLinkConfigPath: String,
    val wlan24Path: String,
    val wlan5Path: String,
) {
    fun withWanConnectionIndex(index: Int): Tr069ModelProfile {
        require(index in 1..16) { "WAN connection index fuera de rango: $index" }
        val igd = "InternetGatewayDevice"
        return copy(
            wanIpConnectionPath =
                "$igd.WANDevice.1.WANConnectionDevice.$index.WANIPConnection.1",
            wanGponLinkConfigPath =
                "$igd.WANDevice.1.WANConnectionDevice.$index.X_CT-COM_WANGponLinkConfig",
        )
    }

    fun buildParameterValues(
        ip: String,
        subnetMask: String,
        gateway: String,
        dns: String,
        vlanId: Int,
        wifiSsid24: String?,
        wifiPassword24: String?,
        wifiSsid5: String?,
        wifiPassword5: String?,
    ): List<Tr069ParameterValue> {
        val vlan = vlanId.toString()
        val values = mutableListOf(
            param("$wanIpConnectionPath.AddressingType", "Static", "xsd:string"),
            param("$wanIpConnectionPath.ExternalIPAddress", ip, "xsd:string"),
            param("$wanIpConnectionPath.SubnetMask", subnetMask, "xsd:string"),
            param("$wanIpConnectionPath.DefaultGateway", gateway, "xsd:string"),
            param("$wanIpConnectionPath.DNSServers", dns, "xsd:string"),
            param("$wanIpConnectionPath.DNSEnabled", "true", "xsd:boolean"),
            param("$wanIpConnectionPath.X_CT-COM_VLANIDMark", vlan, "xsd:unsignedInt"),
            param("$wanIpConnectionPath.X_ZTE-COM_VLANID", vlan, "xsd:unsignedInt"),
            param("$wanIpConnectionPath.X_ZTE-COM_VLANEnable", "1", "xsd:unsignedInt"),
            param("$wanGponLinkConfigPath.VLANIDMark", vlan, "xsd:unsignedInt"),
        )
        if (!wifiSsid24.isNullOrBlank()) {
            values += param("$wlan24Path.SSID", wifiSsid24, "xsd:string")
        }
        if (!wifiPassword24.isNullOrBlank()) {
            values += param("$wlan24Path.KeyPassphrase", wifiPassword24, "xsd:string")
        }
        if (!wifiSsid5.isNullOrBlank()) {
            values += param("$wlan5Path.SSID", wifiSsid5, "xsd:string")
        }
        if (!wifiPassword5.isNullOrBlank()) {
            values += param("$wlan5Path.KeyPassphrase", wifiPassword5, "xsd:string")
        }
        return values
    }

    private fun param(path: String, value: String, type: String) =
        Tr069ParameterValue(path = path, value = value, type = type)
}

object Tr069ModelProfiles {

    private const val IGD = "InternetGatewayDevice"
    /** Fallback when GenieACS has no WAN tree yet (factory ONU: single WAN at index 1). */
    private const val DEFAULT_WAN_INDEX = 1
    private const val WAN1 =
        "$IGD.WANDevice.1.WANConnectionDevice.$DEFAULT_WAN_INDEX.WANIPConnection.1"
    private const val WAN_GPON =
        "$IGD.WANDevice.1.WANConnectionDevice.$DEFAULT_WAN_INDEX.X_CT-COM_WANGponLinkConfig"
    private const val WLAN_24 = "$IGD.LANDevice.1.WLANConfiguration.5"
    private const val WLAN_5 = "$IGD.LANDevice.1.WLANConfiguration.1"

    private val V2804AX15T = Tr069ModelProfile(
        productClass = "V2804AX15T",
        wanIpConnectionPath = WAN1,
        wanGponLinkConfigPath = WAN_GPON,
        wlan24Path = WLAN_24,
        wlan5Path = WLAN_5,
    )

    private val BY_KEY = mapOf(
        "V2804AX15T" to V2804AX15T,
        // SmartOLT onu_type_name for VSOL V2804AX15T CPEs
        "VSOLVA74" to V2804AX15T,
    )

    fun resolve(onuTypeName: String?, productClass: String?): Tr069ModelProfile? {
        val keys = listOfNotNull(onuTypeName, productClass)
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
        return keys.firstNotNullOfOrNull { key ->
            BY_KEY.entries.firstOrNull { (k, _) -> key.contains(k) }?.value
        }
    }

    /**
     * Uses the first WANConnectionDevice index reported by GenieACS.
     * On a factory ONU there is only one WAN slot; no preference for a fixed index.
     * Falls back to [DEFAULT_WAN_INDEX] (1) when the tree is not available yet.
     */
    fun resolveWanConnectionIndex(existingIndices: Collection<Int>): Int {
        val sorted = existingIndices.filter { it in 1..16 }.sorted()
        return sorted.firstOrNull() ?: DEFAULT_WAN_INDEX
    }
}
