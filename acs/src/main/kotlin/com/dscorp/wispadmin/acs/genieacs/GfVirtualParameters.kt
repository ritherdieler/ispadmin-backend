package com.dscorp.wispadmin.acs.genieacs

object GfVirtualParameters {
    const val APPLY_INTERNET_PPPOE = "VirtualParameters.GfApplyInternetPppoe"
    const val APPLY_INTERNET_STATIC = "VirtualParameters.GfApplyInternetStatic"
    const val SET_WIFI = "VirtualParameters.GfSetWifi"
    const val REBOOT = "VirtualParameters.GfReboot"
    const val INTERNET_STATUS = "VirtualParameters.GfInternetStatus"
    const val WIFI_STATUS = "VirtualParameters.GfWifiStatus"

    private val PPP_MODELS = setOf("V2804AX15T", "F6600R")

    fun supportedPppProductClass(productClass: String?): Boolean {
        val key = productClass?.trim()?.uppercase() ?: return false
        return key in PPP_MODELS
    }
}
