package com.dscorp.wispadmin.wispadmin.cpe

/** Where the WAN profile of a CPE is actually provisioned. */
enum class WanManagement { TR069, OLT_OMCI }

data class CpeCapabilities(
    val canWriteWanViaTr069: Boolean,
    val canWriteWanViaOmci: Boolean,
    val canWriteWifiViaTr069: Boolean,
    val wanManagedBy: WanManagement,
) {
    companion object {
        val FULL_TR069 = CpeCapabilities(
            canWriteWanViaTr069 = true,
            canWriteWanViaOmci = true,
            canWriteWifiViaTr069 = true,
            wanManagedBy = WanManagement.TR069,
        )

        val OMCI_MANAGED_WAN = CpeCapabilities(
            canWriteWanViaTr069 = false,
            canWriteWanViaOmci = true,
            canWriteWifiViaTr069 = true,
            wanManagedBy = WanManagement.OLT_OMCI,
        )
    }
}

/** Identity of a CPE as advertised to the ACS, used to resolve its capabilities. */
data class CpeDeviceProfile(
    val serialNumber: String,
    val vendor: String?,
    val model: String?,
)

object CpeWarnings {
    const val WAN_NOT_MANAGEABLE =
        "Este equipo no admite configuración WAN remota por TR-069 ni por la OLT; revise el aprovisionamiento."

    const val ONU_NOT_FOUND_IN_OLT =
        "No se encontró la ONU en la OLT para el serial indicado; no se aplicó la configuración de red."

    const val WAN_NOT_WRITABLE =
        "El equipo no expone una conexión WAN IP configurable por TR-069; se omitió la configuración de red."

    const val VLAN_NOT_WRITABLE =
        "El equipo no expone un parámetro VLAN writable por TR-069; la VLAN no se modificó."

    const val WIFI_NOT_SUPPORTED =
        "El equipo no permite configurar el Wi-Fi por TR-069."
}
