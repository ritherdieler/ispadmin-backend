package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.cpe.CpeWarnings
import com.dscorp.wispadmin.wispadmin.data.model.NapBox
import com.dscorp.wispadmin.wispadmin.data.model.Onu
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.MoveOnuRequest
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.OnuAuthorizationRequest
import com.dscorp.wispadmin.wispadmin.response.OnuBySnResponse
import com.dscorp.wispadmin.wispadmin.response.Response
import com.dscorp.wispadmin.wispadmin.response.UnconfirmedOnuResponse
import com.dscorp.wispadmin.wispadmin.util.HttpClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap

@Service
class RealOltService(
    @Value("\${olt.service.base-url}") private val baseUrl: String,
    @Value("\${olt.service.api-key}") private val apiKey: String
) : OltService {

    private val logger = LoggerFactory.getLogger(RealOltService::class.java)

    override fun getUnConfiguredOnus(): List<Response>? {
        return try {
            HttpClient.get(baseUrl, apiKey, "onu/unconfigured_onus", UnconfirmedOnuResponse::class.java).response
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override fun getOnuBySn(onuSn: String): OnuBySnResponse {
        return HttpClient.get(baseUrl, apiKey, "onu/get_onus_details_by_sn/$onuSn", OnuBySnResponse::class.java)
    }

    override fun moveOnu(request: MoveOnuRequest, onu: Onu, newNapBox: NapBox) {
        val body: MultiValueMap<String, Any> = LinkedMultiValueMap()
        body.apply {
            add("olt_id", newNapBox.oltId)
            add("board", newNapBox.oltBoard)
            add("port", newNapBox.oltPort)
        }
        HttpClient.post(baseUrl, apiKey, "onu/move/${onu.sn}", body, Any::class.java)
    }

    override fun authorizeOnuInSmartOltWidthPostMethod(authorizationRequest: OnuAuthorizationRequest) {
        with(authorizationRequest) {
            val body: MultiValueMap<String, Any> = LinkedMultiValueMap()
            body.add("olt_id", olt_id)
            body.add("pon_type", pon_type)
            body.add("board", board)
            body.add("port", port)
            body.add("sn", sn)
            body.add("vlan", vlan)
            body.add("onu_type", onu_type)
            body.add("zone", zone)
            body.add("name", name)
            body.add("onu_mode", onu_mode)
            body.add("custom_profile", custom_profile)

            HttpClient.post(baseUrl, apiKey, "onu/authorize_onu", body, Any::class.java)
        }
    }

    override fun deleteOnu(onuExternalId: String) {
        HttpClient.post(baseUrl, apiKey, "onu/delete/$onuExternalId", null, Any::class.java)
    }

    override fun rebootOnu(uniqueExternalId: String) {
        HttpClient.post(baseUrl, apiKey, "onu/reboot/$uniqueExternalId", null, Any::class.java)
    }

    override fun updateOnuWanConfig(
        sn: String,
        vlan: Int?,
        ip: String?,
        mask: String?,
        gateway: String?,
        dns1: String?,
        dns2: String?,
    ): OnuWanUpdateResult {
        val hasStaticWan = listOf(ip, mask, gateway, dns1, dns2).any { !it.isNullOrBlank() }
        if (vlan == null && !hasStaticWan) return OnuWanUpdateResult(applied = false)

        val uniqueExternalId = resolveUniqueExternalId(sn)
            ?: return OnuWanUpdateResult(applied = false, warnings = listOf(CpeWarnings.ONU_NOT_FOUND_IN_OLT))

        val warnings = mutableListOf<String>()
        val applied = if (hasStaticWan) {
            val body: MultiValueMap<String, Any> = LinkedMultiValueMap()
            body.add("wan_mode", STATIC_WAN_MODE)
            ip.addTo(body, "ip_address")
            mask.addTo(body, "subnet_mask")
            gateway.addTo(body, "default_gateway")
            dns1.addTo(body, "dns1")
            dns2.addTo(body, "dns2")
            vlan?.let { body.add("vlan", it.toString()) }
            post("$SET_WAN_MODE_PATH/$uniqueExternalId", body, "la configuración WAN", warnings)
        } else {
            val body: MultiValueMap<String, Any> = LinkedMultiValueMap()
            body.add("vlan", vlan.toString())
            post("$UPDATE_VLAN_PATH/$uniqueExternalId", body, "la VLAN", warnings)
        }

        return OnuWanUpdateResult(
            applied = applied,
            uniqueExternalId = uniqueExternalId,
            warnings = warnings,
        )
    }

    private fun resolveUniqueExternalId(sn: String): String? = try {
        getOnuBySn(sn).onus.firstOrNull()?.unique_external_id?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        logger.warn("No se pudo resolver el unique_external_id de la ONU $sn: ${e.message}")
        null
    }

    private fun post(
        path: String,
        body: MultiValueMap<String, Any>,
        subject: String,
        warnings: MutableList<String>,
    ): Boolean = try {
        HttpClient.post(baseUrl, apiKey, path, body, Any::class.java)
        true
    } catch (e: Exception) {
        logger.error("Fallo al actualizar $subject en la OLT ($path)", e)
        warnings += "No se pudo actualizar $subject en la OLT: ${e.message}"
        false
    }

    private fun String?.addTo(body: MultiValueMap<String, Any>, field: String) {
        this?.trim()?.takeIf { it.isNotEmpty() }?.let { body.add(field, it) }
    }

    private companion object {
        const val SET_WAN_MODE_PATH = "onu/set_wan_mode"
        const val UPDATE_VLAN_PATH = "onu/update_vlan"
        const val STATIC_WAN_MODE = "static"
    }
}
