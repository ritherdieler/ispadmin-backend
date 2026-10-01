package com.dscorp.wispadmin.acs.genieacs

data class OnboardingV2CpeLayout(
    val wanConnectionDevice: Int,
    val wifi24Index: Int,
    val wifi5Index: Int,
    val pppExternalIp: String,
    val ipExternalIp: String,
    val ssid24: String,
    val ssid5: String,
)

object OnboardingV2CpeLayouts {
    private const val WAN = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice"
    private const val WLAN = "InternetGatewayDevice.LANDevice.1.WLANConfiguration"

    private val F6600 = layout(wan = 1, ppp = 2, ip = 2, wifi24 = 1, wifi5 = 5)
    private val VSOL = layout(wan = 2, ppp = 1, ip = 1, wifi24 = 5, wifi5 = 1)
    private val HUAWEI = layout(wan = 2, ppp = 1, ip = 1, wifi24 = 1, wifi5 = 5)

    private val byProductClass = mapOf(
        "F6600R" to F6600,
        "V2804AX15T" to VSOL,
        "VSOLVA74" to VSOL,
        "HG8145X6" to HUAWEI,
    )

    fun of(productClass: String?): OnboardingV2CpeLayout? =
        byProductClass[productClass?.trim()?.uppercase()]

    fun supported(productClass: String?): Boolean = of(productClass) != null

    private fun layout(wan: Int, ppp: Int, ip: Int, wifi24: Int, wifi5: Int) = OnboardingV2CpeLayout(
        wanConnectionDevice = wan,
        wifi24Index = wifi24,
        wifi5Index = wifi5,
        pppExternalIp = "$WAN.$wan.WANPPPConnection.$ppp.ExternalIPAddress",
        ipExternalIp = "$WAN.$wan.WANIPConnection.$ip.ExternalIPAddress",
        ssid24 = "$WLAN.$wifi24.SSID",
        ssid5 = "$WLAN.$wifi5.SSID",
    )
}
