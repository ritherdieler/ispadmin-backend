package com.dscorp.wispadmin.wispadmin.service.genieacs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Tr069WifiSecurityDefaultsTest {

    @Test
    fun `detectFromCsvExport applies prep for any brand when KeyPassphrase is empty`() {
        val wlan24 = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1"
        val prep = Tr069WifiSecurityDefaults.detectFromCsvExport(
            wlanPaths = listOf(wlan24),
            keyPassphraseByPath = mapOf("$wlan24.KeyPassphrase" to ""),
        )
        assertTrue(prep.isNotEmpty())
        assertTrue(prep.any { it.parameterSuffix == "BeaconType" })
    }

    @Test
    fun `detectFromCsvExport skips prep when KeyPassphrase already set`() {
        val wlan5 = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5"
        val prep = Tr069WifiSecurityDefaults.detectFromCsvExport(
            wlanPaths = listOf(wlan5),
            keyPassphraseByPath = mapOf("$wlan5.KeyPassphrase" to "11111111"),
        )
        assertTrue(prep.isEmpty())
    }
}
