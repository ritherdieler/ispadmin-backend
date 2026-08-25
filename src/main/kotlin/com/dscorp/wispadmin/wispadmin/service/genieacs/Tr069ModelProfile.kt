package com.dscorp.wispadmin.wispadmin.service.genieacs

data class Tr069ParameterValue(
    val path: String,
    val value: String,
    val type: String,
)

data class Tr069ModelProfile(
    val productClass: String,
    val wanConnectionDeviceIndex: Int = 1,
    val wanIpConnectionPath: String,
    val wanGponLinkConfigPath: String? = null,
    val vlanParameters: List<Tr069VlanParameterSpec> = emptyList(),
    val wlan24Path: String,
    val wlan5Path: String,
    val wifiSecurityPrep: List<Tr069WifiSecurityPrepSpec> = emptyList(),
    val clientWanIpConnectionPath: String? = null,
    val clientVlanParameters: List<Tr069VlanParameterSpec> = emptyList(),
) {
    fun withWanConnectionIndex(index: Int): Tr069ModelProfile {
        require(index in 1..16) { "WAN connection index fuera de rango: $index" }
        if (index == wanConnectionDeviceIndex) return this
        return copy(
            wanConnectionDeviceIndex = index,
            wanIpConnectionPath = rewriteWanIndex(wanIpConnectionPath, index),
            wanGponLinkConfigPath = wanGponLinkConfigPath?.let { rewriteWanIndex(it, index) },
            vlanParameters = vlanParameters.map { spec ->
                spec.copy(path = rewriteWanIndex(spec.path, index))
            },
        )
    }

    /**
     * WAN de abonado: perfiles VSOL/Huawei reescriben WCD.{globalClientWanIndex} bajo WANDevice.1.
     * F6600R (y similares) declaran [clientWanIpConnectionPath] en otro WANDevice y no tocan WCD.1.
     */
    fun forClientInternetWan(globalClientWanIndex: Int): Tr069ModelProfile {
        val clientPath = clientWanIpConnectionPath?.takeIf { it.isNotBlank() }
            ?: return withWanConnectionIndex(globalClientWanIndex)
        return copy(
            wanConnectionDeviceIndex = wcdIndexFromPath(clientPath),
            wanIpConnectionPath = clientPath,
            wanGponLinkConfigPath = null,
            vlanParameters = clientVlanParameters,
        )
    }

    fun wcdParentPath(): String {
        val wcdInstance = wanIpConnectionPath.substringBefore(".WANIPConnection")
        return wcdInstance.substringBeforeLast('.')
    }

    fun clientWanSlotIndex(): Int = wcdIndexFromPath(wanIpConnectionPath)

    /** Prep staging (192.168.255.x): DHCP + VLAN antes del SPV monolítico de producción. */
    fun buildStagingDhcpParameterValues(vlanId: Int): List<Tr069ParameterValue> {
        val values = mutableListOf(
            param("$wanIpConnectionPath.AddressingType", "DHCP", "xsd:string"),
        )
        values += vlanParameterValues(vlanId)
        return values
    }

    fun buildClientInternetWanParameterValues(
        ip: String,
        subnetMask: String,
        gateway: String,
        dns: String,
        vlanId: Int,
        connectionName: String,
    ): List<Tr069ParameterValue> {
        val values = mutableListOf(
            param("$wanIpConnectionPath.Enable", "true", "xsd:boolean"),
            param("$wanIpConnectionPath.ConnectionType", "IP_Routed", "xsd:string"),
            param("$wanIpConnectionPath.Name", connectionName, "xsd:string"),
            param("$wanIpConnectionPath.X_CT-COM_ServiceList", "INTERNET", "xsd:string"),
            param("$wanIpConnectionPath.X_ZTE-COM_ServiceList", "INTERNET", "xsd:string"),
            param("$wanIpConnectionPath.NATEnabled", "true", "xsd:boolean"),
        )
        values += buildWanParameterValues(ip, subnetMask, gateway, dns, vlanId)
        return values
    }

    fun buildWanParameterValues(
        ip: String,
        subnetMask: String,
        gateway: String,
        dns: String,
        vlanId: Int,
    ): List<Tr069ParameterValue> {
        val values = mutableListOf(
            param("$wanIpConnectionPath.AddressingType", "Static", "xsd:string"),
            param("$wanIpConnectionPath.ExternalIPAddress", ip, "xsd:string"),
            param("$wanIpConnectionPath.SubnetMask", subnetMask, "xsd:string"),
            param("$wanIpConnectionPath.DefaultGateway", gateway, "xsd:string"),
            param("$wanIpConnectionPath.DNSServers", dns, "xsd:string"),
            param("$wanIpConnectionPath.DNSEnabled", "true", "xsd:boolean"),
        )
        values += vlanParameterValues(vlanId)
        return values
    }

    fun buildWifiParameterValues(
        wifiSsid24: String?,
        wifiPassword24: String?,
        wifiSsid5: String?,
        wifiPassword5: String?,
    ): List<Tr069ParameterValue> {
        val values = mutableListOf<Tr069ParameterValue>()
        values += wlanBandValues(wlan24Path, wifiSsid24, wifiPassword24)
        values += wlanBandValues(wlan5Path, wifiSsid5, wifiPassword5)
        return values
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
    ): List<Tr069ParameterValue> =
        buildWanParameterValues(ip, subnetMask, gateway, dns, vlanId) +
            buildWifiParameterValues(wifiSsid24, wifiPassword24, wifiSsid5, wifiPassword5)

    private fun wlanBandValues(
        wlanPath: String,
        ssid: String?,
        password: String?,
    ): List<Tr069ParameterValue> {
        if (wlanPath.isBlank()) return emptyList()
        val values = mutableListOf<Tr069ParameterValue>()
        wifiSecurityPrep.forEach { spec ->
            values += param("$wlanPath.${spec.parameterSuffix}", spec.value, spec.type)
        }
        if (!ssid.isNullOrBlank()) {
            values += param("$wlanPath.SSID", ssid, "xsd:string")
        }
        if (!password.isNullOrBlank()) {
            values += param("$wlanPath.KeyPassphrase", password, "xsd:string")
        }
        return values
    }

    private fun vlanParameterValues(vlanId: Int): List<Tr069ParameterValue> {
        val specs = if (vlanParameters.isNotEmpty()) {
            vlanParameters
        } else {
            defaultVsolVlanSpecs()
        }
        val vlan = vlanId.toString()
        return specs.map { spec ->
            val (value, type) = when (spec.valueKind) {
                Tr069VlanValueKind.VLAN_ID -> vlan to "xsd:unsignedInt"
                Tr069VlanValueKind.ENABLE_ONE -> "1" to "xsd:unsignedInt"
                Tr069VlanValueKind.ENABLE_TRUE -> "true" to "xsd:boolean"
            }
            param(spec.path, value, type)
        }
    }

    private fun defaultVsolVlanSpecs(): List<Tr069VlanParameterSpec> {
        val gponVlan = wanGponLinkConfigPath?.let { Tr069VlanParameterSpec("$it.VLANIDMark") }
        return listOfNotNull(
            Tr069VlanParameterSpec("$wanIpConnectionPath.X_CT-COM_VLANIDMark"),
            Tr069VlanParameterSpec("$wanIpConnectionPath.X_ZTE-COM_VLANID"),
            Tr069VlanParameterSpec(
                path = "$wanIpConnectionPath.X_ZTE-COM_VLANEnable",
                valueKind = Tr069VlanValueKind.ENABLE_ONE,
            ),
            gponVlan,
        )
    }

    private fun rewriteWanIndex(path: String, newIndex: Int): String =
        path.replace(
            Regex("""\.WANConnectionDevice\.\d+\."""),
            ".WANConnectionDevice.$newIndex.",
        )

    private fun wcdIndexFromPath(path: String): Int =
        Regex("""\.WANConnectionDevice\.(\d+)\.""")
            .find(path)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?: wanConnectionDeviceIndex

    private fun param(path: String, value: String, type: String) =
        Tr069ParameterValue(path = path, value = value, type = type)
}

object Tr069ModelProfiles {

    const val STAGING_WAN_INDEX = 1
    const val CLIENT_WAN_INDEX = 2

    private const val IGD = "InternetGatewayDevice"
    private const val DEFAULT_WAN_INDEX = STAGING_WAN_INDEX
    private const val WAN1 =
        "$IGD.WANDevice.1.WANConnectionDevice.$DEFAULT_WAN_INDEX.WANIPConnection.1"
    private const val WAN_GPON =
        "$IGD.WANDevice.1.WANConnectionDevice.$DEFAULT_WAN_INDEX.X_CT-COM_WANGponLinkConfig"
    private const val WLAN_24 = "$IGD.LANDevice.1.WLANConfiguration.5"
    private const val WLAN_5 = "$IGD.LANDevice.1.WLANConfiguration.1"

    private val V2804AX15T = Tr069ModelProfile(
        productClass = "V2804AX15T",
        wanConnectionDeviceIndex = DEFAULT_WAN_INDEX,
        wanIpConnectionPath = WAN1,
        wanGponLinkConfigPath = WAN_GPON,
        wlan24Path = WLAN_24,
        wlan5Path = WLAN_5,
    )

    private val BUILTIN_BY_KEY = mapOf(
        "V2804AX15T" to V2804AX15T,
        "VSOLVA74" to V2804AX15T,
    )

    @Volatile
    private var dynamicResolver: ((String?, String?) -> Tr069ModelProfile?)? = null

    fun registerDynamicResolver(resolver: (String?, String?) -> Tr069ModelProfile?) {
        dynamicResolver = resolver
    }

    fun resolve(onuTypeName: String?, productClass: String?): Tr069ModelProfile? =
        dynamicResolver?.invoke(onuTypeName, productClass)

    /** Perfiles embebidos VSOL; solo para tests unitarios. Producción usa perfiles importados en BD. */
    internal fun resolveBuiltin(onuTypeName: String?, productClass: String?): Tr069ModelProfile? {
        val keys = listOfNotNull(onuTypeName, productClass)
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
        return keys.firstNotNullOfOrNull { key ->
            BUILTIN_BY_KEY.entries.firstOrNull { (k, _) -> key.contains(k) }?.value
        }
    }

    fun resolveWanConnectionIndex(existingIndices: Collection<Int>): Int {
        val sorted = existingIndices.filter { it in 1..16 }.sorted()
        return sorted.firstOrNull() ?: DEFAULT_WAN_INDEX
    }
}
