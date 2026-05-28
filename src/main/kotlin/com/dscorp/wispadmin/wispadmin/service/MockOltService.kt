package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.NapBox
import com.dscorp.wispadmin.wispadmin.data.model.Onu
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.MoveOnuRequest
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.OnuAuthorizationRequest
import com.dscorp.wispadmin.wispadmin.response.OnuBySnResponse
import com.dscorp.wispadmin.wispadmin.response.Response
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class MockOltService : OltService {

    private val logger = LoggerFactory.getLogger(MockOltService::class.java)
    
    // Simulación de estado en memoria para desarrollo
    private val authorizedOnus = ConcurrentHashMap<String, OnuAuthorizationRequest>()
    private val unconfiguredOnus = mutableListOf<Response>()
    private val onuDetails = ConcurrentHashMap<String, OnuBySnResponse>()

    init {
        // Datos mock iniciales para desarrollo
        initializeMockData()
    }

    private fun initializeMockData() {
        logger.info("Inicializando datos mock para OLT en desarrollo")
        
        // ONUs no configuradas mock
        unconfiguredOnus.addAll(listOf(
            Response(
                board = "1",
                olt_id = "1",
                onu = "1",
                onu_type_id = "1",
                onu_type_name = "HG8240H",
                pon_type = "gpon",
                port = "1",
                sn = "ALCL12345678"
            ),
            Response(
                board = "1",
                olt_id = "1", 
                onu = "2",
                onu_type_id = "1",
                onu_type_name = "HG8240H",
                pon_type = "gpon",
                port = "2",
                sn = "ALCL87654321"
            )
        ))

        // Detalles de ONU mock
        onuDetails["ALCL12345678"] = OnuBySnResponse(
            onus = listOf(
                com.dscorp.wispadmin.wispadmin.response.Onu(
                    address = "192.168.1.100",
                    administrative_status = "online",
                    authorization_date = "2024-01-01 00:00:00",
                    board = "1",
                    catv = "disabled",
                    custom_template_name = "default",
                    default_gateway = "192.168.1.1",
                    dns1 = "8.8.8.8",
                    dns2 = "8.8.4.4",
                    ethernet_ports = emptyList(),
                    ip_address = "192.168.1.100",
                    iptv = "disabled",
                    iptv_allowed_macs = "",
                    iptv_cvlan = "",
                    iptv_download_speed = "",
                    iptv_filtered_macs = "",
                    iptv_service_port = "",
                    iptv_svlan = "",
                    iptv_tag_transform_mode = "",
                    iptv_upload_speed = "",
                    iptv_vlan = "",
                    mgmt_ip_address = "192.168.1.100",
                    mgmt_ip_cvlan = "",
                    mgmt_ip_default_gateway = "192.168.1.1",
                    mgmt_ip_dns1 = "8.8.8.8",
                    mgmt_ip_dns2 = "8.8.4.4",
                    mgmt_ip_mode = "dhcp",
                    mgmt_ip_service_port = "1",
                    mgmt_ip_subnet_mask = "255.255.255.0",
                    mgmt_ip_svlan = "",
                    mgmt_ip_tag_transform_mode = "",
                    mgmt_ip_vlan = "100",
                    mode = "bridge",
                    name = "Mock ONU 1",
                    odb_name = "Mock ODB",
                    olt_id = "1",
                    olt_name = "Mock OLT",
                    onu = "1",
                    onu_type_id = "1",
                    onu_type_name = "HG8240H",
                    password = "",
                    pon_type = "gpon",
                    port = "1",
                    service_ports = emptyList(),
                    sn = "ALCL12345678",
                    subnet_mask = "255.255.255.0",
                    tr069_profile = "",
                    unique_external_id = "mock-onu-1",
                    username = "",
                    vlan = "100",
                    voip_ip_address = "",
                    voip_ip_cvlan = "",
                    voip_ip_default_gateway = "",
                    voip_ip_dns1 = "",
                    voip_ip_dns2 = "",
                    voip_ip_mode = "",
                    voip_ip_service_port = "",
                    voip_ip_subnet_mask = "",
                    voip_ip_svlan = "",
                    voip_ip_tag_transform_mode = "",
                    voip_ip_vlan = "",
                    wan_mode = "bridge",
                    wifi_ports = emptyList(),
                    zone_id = "1",
                    zone_name = "Mock Zone"
                )
            ),
            response_code = "200",
            status = true
        )
    }

    override fun getUnConfiguredOnus(): List<Response>? {
        logger.info("MOCK OLT: Obteniendo ONUs no configuradas - Retornando ${unconfiguredOnus.size} ONUs")
        return unconfiguredOnus.toList()
    }

    override fun getOnuBySn(onuSn: String): OnuBySnResponse {
        logger.info("MOCK OLT: Consultando ONU por SN: $onuSn")
        
        val response = onuDetails[onuSn] ?: OnuBySnResponse(onus = emptyList(), response_code = "404", status = false)
        logger.info("MOCK OLT: ONU encontrada: ${response.onus.isNotEmpty()}")
        
        return response
    }

    override fun moveOnu(request: MoveOnuRequest, onu: Onu, newNapBox: NapBox) {
        logger.info("MOCK OLT: Moviendo ONU ${onu.sn} a OLT ${newNapBox.oltId}, Board ${newNapBox.oltBoard}, Port ${newNapBox.oltPort}")
        
        // Simular delay de red
        Thread.sleep(500)
        
        // Actualizar estado mock
        val existingOnu = onuDetails[onu.sn]
        if (existingOnu != null && existingOnu.onus.isNotEmpty()) {
            val updatedOnu = existingOnu.onus[0].copy(
                olt_id = newNapBox.oltId.toString(),
                board = newNapBox.oltBoard.toString(),
                port = newNapBox.oltPort.toString()
            )
            onuDetails[onu.sn] = OnuBySnResponse(onus = listOf(updatedOnu), response_code = "200", status = true)
        }
        
        logger.info("MOCK OLT: ONU movida exitosamente")
    }

    override fun authorizeOnuInSmartOltWidthPostMethod(authorizationRequest: OnuAuthorizationRequest) {
        logger.info("MOCK OLT: Autorizando ONU ${authorizationRequest.sn} en OLT ${authorizationRequest.olt_id}")
        logger.info("MOCK OLT: Detalles - Board: ${authorizationRequest.board}, Port: ${authorizationRequest.port}, VLAN: ${authorizationRequest.vlan}")
        
        // Simular delay de red
        Thread.sleep(1000)
        
        // Guardar en estado mock
        authorizedOnus[authorizationRequest.sn] = authorizationRequest
        
        // Remover de no configuradas si existe
        unconfiguredOnus.removeAll { it.sn == authorizationRequest.sn }
        
        // Agregar a detalles
        val newOnu = com.dscorp.wispadmin.wispadmin.response.Onu(
            address = "192.168.1.101",
            administrative_status = "online",
            authorization_date = "2024-01-01 00:00:00",
            board = authorizationRequest.board.toString(),
            catv = "disabled",
            custom_template_name = authorizationRequest.custom_profile,
            default_gateway = "192.168.1.1",
            dns1 = "8.8.8.8",
            dns2 = "8.8.4.4",
            ethernet_ports = emptyList(),
            ip_address = "192.168.1.101",
            iptv = "disabled",
            iptv_allowed_macs = "",
            iptv_cvlan = "",
            iptv_download_speed = "",
            iptv_filtered_macs = "",
            iptv_service_port = "",
            iptv_svlan = "",
            iptv_tag_transform_mode = "",
            iptv_upload_speed = "",
            iptv_vlan = "",
            mgmt_ip_address = "192.168.1.101",
            mgmt_ip_cvlan = "",
            mgmt_ip_default_gateway = "192.168.1.1",
            mgmt_ip_dns1 = "8.8.8.8",
            mgmt_ip_dns2 = "8.8.4.4",
            mgmt_ip_mode = "dhcp",
            mgmt_ip_service_port = "1",
            mgmt_ip_subnet_mask = "255.255.255.0",
            mgmt_ip_svlan = "",
            mgmt_ip_tag_transform_mode = "",
            mgmt_ip_vlan = authorizationRequest.vlan.toString(),
            mode = authorizationRequest.onu_mode,
            name = authorizationRequest.name,
            odb_name = "Mock ODB",
            olt_id = authorizationRequest.olt_id.toString(),
            olt_name = "Mock OLT",
            onu = "1",
            onu_type_id = "1",
            onu_type_name = authorizationRequest.onu_type,
            password = "",
            pon_type = authorizationRequest.pon_type,
            port = authorizationRequest.port.toString(),
            service_ports = emptyList(),
            sn = authorizationRequest.sn,
            subnet_mask = "255.255.255.0",
            tr069_profile = "",
            unique_external_id = "mock-${authorizationRequest.sn}",
            username = "",
            vlan = authorizationRequest.vlan.toString(),
            voip_ip_address = "",
            voip_ip_cvlan = "",
            voip_ip_default_gateway = "",
            voip_ip_dns1 = "",
            voip_ip_dns2 = "",
            voip_ip_mode = "",
            voip_ip_service_port = "",
            voip_ip_subnet_mask = "",
            voip_ip_svlan = "",
            voip_ip_tag_transform_mode = "",
            voip_ip_vlan = "",
            wan_mode = "bridge",
            wifi_ports = emptyList(),
            zone_id = authorizationRequest.zone,
            zone_name = authorizationRequest.zone
        )
        onuDetails[authorizationRequest.sn] = OnuBySnResponse(onus = listOf(newOnu), response_code = "200", status = true)
        
        logger.info("MOCK OLT: ONU autorizada exitosamente")
    }

    override fun rebootOnu(uniqueExternalId: String) {
        logger.info("MOCK OLT: Reiniciando ONU con ID externo: $uniqueExternalId")
        Thread.sleep(300)
    }

    override fun deleteOnu(onuExternalId: String) {
        logger.info("MOCK OLT: Eliminando ONU con ID externo: $onuExternalId")
        
        // Simular delay de red
        Thread.sleep(300)
        
        // Remover de estado mock
        authorizedOnus.entries.removeIf { it.value.sn == onuExternalId }
        onuDetails.remove(onuExternalId)
        
        logger.info("MOCK OLT: ONU eliminada exitosamente")
    }

    // Métodos auxiliares para debugging en desarrollo
    fun getAuthorizedOnusCount(): Int = authorizedOnus.size
    fun getUnconfiguredOnusCount(): Int = unconfiguredOnus.size
    fun getOnuDetailsCount(): Int = onuDetails.size
    
    fun clearMockData() {
        logger.info("MOCK OLT: Limpiando datos mock")
        authorizedOnus.clear()
        unconfiguredOnus.clear()
        onuDetails.clear()
        initializeMockData()
    }
}
