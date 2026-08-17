package com.dscorp.wispadmin.wispadmin.genieacs

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

class GenieAcsClient(
    private val webClient: WebClient,
    private val objectMapper: ObjectMapper = ObjectMapper(),
) {

    fun updateWifi(sn: String, ssid: String, passphrase: String) {
        val deviceId = findDevice(sn)?._id
            ?: throw GenieAcsDeviceNotFoundException(sn)
        val payload = mapOf(
            "name" to "setParameterValues",
            "parameterValues" to listOf(
                listOf(TR098_SSID, ssid, "xsd:string"),
                listOf(TR098_PSK, passphrase, "xsd:string"),
                listOf(TR181_SSID, ssid, "xsd:string"),
                listOf(TR181_PASSPHRASE, passphrase, "xsd:string"),
            )
        )
        webClient.post()
            .uri { it.pathSegment("devices", deviceId, "tasks").build() }
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .retrieve()
            .toBodilessEntity()
            .block()
    }

    fun getLastInform(sn: String): String? = findDevice(sn)?.lastInform

    private fun findDevice(sn: String): GenieAcsDeviceRef? {
        val query = objectMapper.writeValueAsString(mapOf("_deviceId._SerialNumber" to sn))
        val devices = webClient.get()
            .uri("/devices/?query={query}&projection={projection}", query, "_id,_lastInform")
            .retrieve()
            .bodyToMono(object : ParameterizedTypeReference<List<JsonNode>>() {})
            .block()
            .orEmpty()
        val first = devices.firstOrNull() ?: return null
        val id = first.path("_id").asText(null)?.takeIf { it.isNotBlank() } ?: return null
        val lastInform = first.path("_lastInform").asText(null)?.takeIf { it.isNotBlank() }
        return GenieAcsDeviceRef(_id = id, lastInform = lastInform)
    }

    private data class GenieAcsDeviceRef(
        val _id: String,
        val lastInform: String?,
    )

    companion object {
        const val TR098_SSID = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.SSID"
        const val TR098_PSK = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.PreSharedKey.1.PreSharedKey"
        const val TR181_SSID = "Device.WiFi.SSID.1.SSID"
        const val TR181_PASSPHRASE = "Device.WiFi.AccessPoint.1.Security.KeyPassphrase"
    }
}
