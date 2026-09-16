package com.dscorp.wispadmin.acs.genieacs

data class NamedCpeLayout(
    val pppExternalIp: String,
    val ipExternalIp: String,
    val ssid24: String,
    val ssid5: String,
)

object NamedCpeLayouts {
    private const val LAN = "InternetGatewayDevice.LANDevice.1.WLANConfiguration"
    private const val WAN = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice"

    private val F6600 = NamedCpeLayout(
        pppExternalIp = "$WAN.1.WANPPPConnection.2.ExternalIPAddress",
        ipExternalIp = "$WAN.1.WANIPConnection.2.ExternalIPAddress",
        ssid24 = "$LAN.1.SSID",
        ssid5 = "$LAN.5.SSID",
    )

    private val VSOL = NamedCpeLayout(
        pppExternalIp = "$WAN.2.WANPPPConnection.1.ExternalIPAddress",
        ipExternalIp = "$WAN.2.WANIPConnection.1.ExternalIPAddress",
        ssid24 = "$LAN.5.SSID",
        ssid5 = "$LAN.1.SSID",
    )

    private val BY_CLASS = mapOf(
        "F6600R" to F6600,
        "V2804AX15T" to VSOL,
        "VSOLVA74" to VSOL,
    )

    fun of(productClass: String?): NamedCpeLayout? {
        val key = productClass?.trim()?.uppercase() ?: return null
        return BY_CLASS.entries.firstOrNull { it.key.uppercase() == key }?.value
    }

    fun supported(productClass: String?): Boolean = of(productClass) != null
}
