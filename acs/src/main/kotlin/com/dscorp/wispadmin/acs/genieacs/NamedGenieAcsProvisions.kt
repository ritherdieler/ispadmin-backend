package com.dscorp.wispadmin.acs.genieacs

object NamedGenieAcsProvisions {
    const val PPPOE = "gf-pppoe-wan2-poc"
    const val STATIC = "gf-static-wan2-poc"
    const val WIFI = "gf-wifi-ssid-poc"
    const val REBOOT = "gf-reboot-poc"
    const val MIN_WIFI_PASSPHRASE = 8

    val ids = listOf(PPPOE, STATIC, WIFI, REBOOT)

    fun classpathPath(id: String): String = "/genieacs/provisions/$id.js"
}
