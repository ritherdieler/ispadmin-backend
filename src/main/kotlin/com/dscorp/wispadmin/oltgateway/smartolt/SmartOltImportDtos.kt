package com.dscorp.wispadmin.oltgateway.smartolt

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class SmartOltZonesResponseDto(
    @JsonProperty("response") val response: List<SmartOltZoneDto> = emptyList(),
    @JsonProperty("status") val status: Boolean = false
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SmartOltOnuTypesResponseDto(
    @JsonProperty("response") val response: List<SmartOltOnuTypeCatalogDto> = emptyList(),
    @JsonProperty("status") val status: Boolean = false
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SmartOltZoneDto(
    @JsonProperty("id") val id: String = "",
    @JsonProperty("name") val name: String = ""
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SmartOltOnuTypeCatalogDto(
    @JsonProperty("id") val id: String = "",
    @JsonProperty("name") val name: String = "",
    @JsonProperty("pon_type") val ponType: String = "gpon",
    @JsonProperty("capability") val capability: String = "bridging_routing",
    @JsonProperty("ethernet_ports") val ethernetPorts: String = "1",
    @JsonProperty("wifi_ports") val wifiPorts: String = "0",
    @JsonProperty("voip_ports") val voipPorts: String = "0",
    @JsonProperty("catv") val catv: String = "0",
    @JsonProperty("allow_custom_profiles") val allowCustomProfiles: String = "1"
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SmartOltAllOnusPageDto(
    @JsonProperty("page") val page: Int = 1,
    @JsonProperty("page_size") val pageSize: Int = 0,
    @JsonProperty("total_items") val totalItems: Int = 0,
    @JsonProperty("total_pages") val totalPages: Int = 0,
    @JsonProperty("onus") val onus: List<SmartOltConfiguredOnuDto> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SmartOltConfiguredOnuDto(
    @JsonProperty("unique_external_id") val uniqueExternalId: String = "",
    @JsonProperty("sn") val sn: String = "",
    @JsonProperty("olt_id") val oltId: String = "",
    @JsonProperty("olt_name") val oltName: String = "",
    @JsonProperty("board") val board: String = "",
    @JsonProperty("port") val port: String = "",
    @JsonProperty("onu") val onu: String = "",
    @JsonProperty("pon_type") val ponType: String = "gpon",
    @JsonProperty("onu_type_id") val onuTypeId: String = "",
    @JsonProperty("onu_type_name") val onuTypeName: String = "",
    @JsonProperty("zone_id") val zoneId: String = "",
    @JsonProperty("zone_name") val zoneName: String = "",
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("address") val address: String? = null,
    @JsonProperty("contact") val contact: String? = null,
    @JsonProperty("latitude") val latitude: String? = null,
    @JsonProperty("longitude") val longitude: String? = null,
    @JsonProperty("odb_name") val odbName: String? = null,
    @JsonProperty("odb_port") val odbPort: String? = null,
    @JsonProperty("mode") val mode: String? = null,
    @JsonProperty("wan_mode") val wanMode: String? = null,
    @JsonProperty("vlan") val vlan: String? = null,
    @JsonProperty("custom_template_name") val customTemplateName: String? = null,
    @JsonProperty("administrative_status") val administrativeStatus: String? = null,
    @JsonProperty("authorization_date") val authorizationDate: String? = null,
    @JsonProperty("is_synced_after_import") val isSyncedAfterImport: String? = null,
    @JsonProperty("is_failed_resync_config") val isFailedResyncConfig: String? = null,
    @JsonProperty("status") val status: String? = null,
    @JsonProperty("signal") val signal: String? = null,
    @JsonProperty("signal_1490") val signal1490: String? = null,
    @JsonProperty("signal_1310") val signal1310: String? = null
)
