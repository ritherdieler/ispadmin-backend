package com.dscorp.wispadmin.oltgateway.api

data class SmartOltUnconfiguredItemDto(
    val board: String = "",
    val olt_id: String = "",
    val onu: String = "",
    val onu_type_id: String = "",
    val onu_type_name: String = "",
    val pon_type: String = "",
    val port: String = "",
    val sn: String = ""
)

data class SmartOltUnconfiguredOnusResponseDto(
    val response: List<SmartOltUnconfiguredItemDto> = emptyList(),
    val status: Boolean = false
)

data class SmartOltOnuDto(
    val address: String = "",
    val administrative_status: String = "",
    val authorization_date: String = "",
    val board: String = "",
    val catv: String = "",
    val custom_template_name: String = "",
    val default_gateway: String = "",
    val dns1: String = "",
    val dns2: String = "",
    val ethernet_ports: List<Any> = emptyList(),
    val ip_address: String = "",
    val iptv: String = "",
    val iptv_allowed_macs: String = "",
    val iptv_cvlan: String = "",
    val iptv_download_speed: String = "",
    val iptv_filtered_macs: String = "",
    val iptv_service_port: String = "",
    val iptv_svlan: String = "",
    val iptv_tag_transform_mode: String = "",
    val iptv_upload_speed: String = "",
    val iptv_vlan: String = "",
    val mgmt_ip_address: String = "",
    val mgmt_ip_cvlan: String = "",
    val mgmt_ip_default_gateway: String = "",
    val mgmt_ip_dns1: String = "",
    val mgmt_ip_dns2: String = "",
    val mgmt_ip_mode: String = "",
    val mgmt_ip_service_port: String? = "",
    val mgmt_ip_subnet_mask: String = "",
    val mgmt_ip_svlan: String = "",
    val mgmt_ip_tag_transform_mode: String = "",
    val mgmt_ip_vlan: String = "",
    val mode: String = "",
    val name: String = "",
    val odb_name: String = "",
    val olt_id: String = "",
    val olt_name: String = "",
    val onu: String = "",
    val onu_type_id: String = "",
    val onu_type_name: String = "",
    val password: String = "",
    val pon_type: String = "",
    val port: String = "",
    val service_ports: List<Any> = emptyList(),
    val sn: String = "",
    val subnet_mask: String = "",
    val tr069_profile: String = "",
    val unique_external_id: String = "",
    val username: String = "",
    val vlan: String = "",
    val voip_ip_address: String = "",
    val voip_ip_cvlan: String = "",
    val voip_ip_default_gateway: String = "",
    val voip_ip_dns1: String = "",
    val voip_ip_dns2: String = "",
    val voip_ip_mode: String = "",
    val voip_ip_service_port: String = "",
    val voip_ip_subnet_mask: String = "",
    val voip_ip_svlan: String = "",
    val voip_ip_tag_transform_mode: String = "",
    val voip_ip_vlan: String = "",
    val wan_mode: String = "",
    val wifi_ports: List<String> = emptyList(),
    val zone_id: String = "",
    val zone_name: String = ""
)

data class SmartOltOnuBySnResponseDto(
    val onus: List<SmartOltOnuDto> = emptyList(),
    val response_code: String = "",
    val status: Boolean = false
)

data class SmartOltActionResponseDto(
    val status: Boolean = true,
    val response_code: String = "200",
    val message: String? = null,
    val unique_external_id: String? = null
)

data class AuthorizeOnuFormDto(
    val olt_id: String = "",
    val pon_type: String = "gpon",
    val board: String = "",
    val port: String = "",
    val sn: String = "",
    val vlan: String = "",
    val onu_type: String = "",
    val zone: String = "",
    val name: String = "",
    val onu_mode: String = "",
    val custom_profile: String = ""
)

data class MoveOnuFormDto(
    val olt_id: String = "",
    val board: String = "",
    val port: String = ""
)
