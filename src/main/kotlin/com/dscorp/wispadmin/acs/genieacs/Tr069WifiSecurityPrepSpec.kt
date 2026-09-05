package com.dscorp.wispadmin.acs.genieacs

data class Tr069WifiSecurityPrepSpec(
    val parameterSuffix: String,
    val value: String,
    val type: String,
)

object Tr069WifiSecurityDefaults {

    /** TR-069 WPA2-PSK prep applied before KeyPassphrase when the radio has no password yet. */
    val STANDARD_OPEN_WIFI_PREP = listOf(
        Tr069WifiSecurityPrepSpec("Enable", "true", "xsd:boolean"),
        Tr069WifiSecurityPrepSpec("BeaconType", "11i", "xsd:string"),
        Tr069WifiSecurityPrepSpec("WPAEncryptionModes", "AESEncryption", "xsd:string"),
        Tr069WifiSecurityPrepSpec("WPAAuthenticationMode", "PSKAuthentication", "xsd:string"),
    )

    fun detectFromCsvExport(
        wlanPaths: Collection<String?>,
        keyPassphraseByPath: Map<String, String?>,
    ): List<Tr069WifiSecurityPrepSpec> {
        val needsPrep = wlanPaths.filterNotNull().any { wlanPath ->
            keyPassphraseByPath["$wlanPath.KeyPassphrase"].isNullOrBlank()
        }
        return if (needsPrep) STANDARD_OPEN_WIFI_PREP else emptyList()
    }
}
