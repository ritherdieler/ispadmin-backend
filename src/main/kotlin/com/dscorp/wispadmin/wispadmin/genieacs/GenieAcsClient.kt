package com.dscorp.wispadmin.wispadmin.genieacs

import com.dscorp.wispadmin.wispadmin.cpe.CpeWarnings
import com.dscorp.wispadmin.wispadmin.dto.CpeNetworkConfigRequest
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import java.time.Instant

data class GenieAcsDeviceDescriptor(
    val deviceId: String,
    val vendor: String?,
    val model: String?,
    val lastInform: String?,
    val reachable: Boolean,
)

data class GenieAcsApplyResult(
    val appliedNetwork: Boolean,
    val appliedWifi: Boolean,
    val warnings: List<String> = emptyList(),
)

class GenieAcsClient(
    private val webClient: WebClient,
    private val objectMapper: ObjectMapper = ObjectMapper(),
    private val maxInformAge: Duration = DEFAULT_MAX_INFORM_AGE,
) {

    fun updateWifi(sn: String, ssid: String, passphrase: String) {
        val device = requireReachableDevice(sn)
        enqueueSetParameterValues(
            deviceId = device.deviceId,
            parameterValues = discoverWifiParameterValues(sn, ssid, passphrase),
        )
    }

    fun applyCpeConfiguration(
        sn: String,
        network: CpeNetworkConfigRequest?,
        ssid: String?,
        passphrase: String?,
    ): GenieAcsApplyResult {
        val device = requireReachableDevice(sn)
        val warnings = mutableListOf<String>()
        val parameterValues = mutableListOf<List<String>>()

        var appliedNetwork = false
        if (network != null) {
            val discovery = discoverWanParameterValues(sn, network)
            warnings += discovery.warnings
            if (discovery.parameterValues.isNotEmpty()) {
                parameterValues += discovery.parameterValues
                appliedNetwork = true
            }
        }

        val writeWifi = !ssid.isNullOrBlank() && !passphrase.isNullOrBlank()
        if (writeWifi) {
            parameterValues += discoverWifiParameterValues(sn, ssid!!, passphrase!!)
        }

        if (parameterValues.isEmpty()) {
            return GenieAcsApplyResult(appliedNetwork = false, appliedWifi = false, warnings = warnings)
        }
        enqueueSetParameterValues(device.deviceId, parameterValues)
        return GenieAcsApplyResult(
            appliedNetwork = appliedNetwork,
            appliedWifi = writeWifi,
            warnings = warnings,
        )
    }

    fun getLastInform(sn: String): String? = findDeviceDescriptor(sn)?.lastInform

    fun findDeviceDescriptor(sn: String): GenieAcsDeviceDescriptor? {
        val query = objectMapper.writeValueAsString(serialLookupQuery(sn))
        val devices = webClient.get()
            .uri("/devices/?query={query}&projection={projection}", query, "_id,_lastInform,_deviceId")
            .retrieve()
            .bodyToMono(object : ParameterizedTypeReference<List<JsonNode>>() {})
            .block()
            .orEmpty()
        val first = devices.firstOrNull() ?: return null
        val id = first.path("_id").asText(null)?.takeIf { it.isNotBlank() } ?: return null
        val lastInform = first.path("_lastInform").asText(null)?.takeIf { it.isNotBlank() }
        val deviceId = first.path("_deviceId")
        return GenieAcsDeviceDescriptor(
            deviceId = id,
            vendor = resolveVendor(deviceId, id),
            model = resolveModel(deviceId, id),
            lastInform = lastInform,
            reachable = isFresh(lastInform),
        )
    }

    private fun requireReachableDevice(sn: String): GenieAcsDeviceDescriptor {
        val device = findDeviceDescriptor(sn) ?: throw GenieAcsDeviceNotFoundException(sn)
        if (!device.reachable) throw CpeDeviceOfflineException(sn, device.lastInform)
        return device
    }

    private fun isFresh(lastInform: String?): Boolean {
        val instant = lastInform?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return false
        return Duration.between(instant, Instant.now()) <= maxInformAge
    }

    private fun resolveVendor(deviceId: JsonNode, id: String): String? =
        deviceId.path("_Manufacturer").asText(null)?.takeIf { it.isNotBlank() }
            ?: deviceId.path("_OUI").asText(null)?.takeIf { it.isNotBlank() }
            ?: id.split(DEVICE_ID_SEPARATOR).takeIf { it.size >= 3 }?.first()

    private fun resolveModel(deviceId: JsonNode, id: String): String? {
        deviceId.path("_ProductClass").asText(null)?.takeIf { it.isNotBlank() }?.let { return it }
        val parts = id.split(DEVICE_ID_SEPARATOR)
        if (parts.size < 3) return null
        return parts.subList(1, parts.size - 1).joinToString(DEVICE_ID_SEPARATOR).takeIf { it.isNotBlank() }
    }

    private fun enqueueSetParameterValues(deviceId: String, parameterValues: List<List<String>>) {
        val payload = mapOf(
            "name" to "setParameterValues",
            "connectionRequest" to true,
            "parameterValues" to parameterValues,
        )
        webClient.post()
            .uri { it.pathSegment("devices", deviceId, "tasks").build() }
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .retrieve()
            .toBodilessEntity()
            .block()
    }

    private fun serialLookupQuery(sn: String): Map<String, Any> {
        val clauses = mutableListOf<Map<String, Any>>(
            mapOf("_deviceId._SerialNumber" to sn),
            mapOf("_id" to mapOf("\$regex" to Regex.escape(sn))),
        )
        val suffix = if (sn.length > 8) sn.takeLast(8) else null
        if (!suffix.isNullOrBlank() && suffix != sn) {
            clauses += mapOf(
                "_deviceId._SerialNumber" to mapOf("\$regex" to "${Regex.escape(suffix)}$"),
            )
        }
        return mapOf("\$or" to clauses)
    }

    private fun discoverWanParameterValues(
        sn: String,
        network: CpeNetworkConfigRequest,
    ): WanDiscovery {
        val query = objectMapper.writeValueAsString(serialLookupQuery(sn))
        val devices = webClient.get()
            .uri(
                "/devices/?query={query}&projection={projection}",
                query,
                "InternetGatewayDevice.WANDevice",
            )
            .retrieve()
            .bodyToMono(object : ParameterizedTypeReference<List<JsonNode>>() {})
            .block()
            .orEmpty()
        val wanDevice = devices.firstOrNull()
            ?.path("InternetGatewayDevice")
            ?.path("WANDevice")
        val target = findPrimaryWanIpConnection(wanDevice)
            ?: return WanDiscovery(emptyList(), listOf(CpeWarnings.WAN_NOT_WRITABLE))

        val dnsServers = buildString {
            append(network.dnsPrimary.trim())
            network.dnsSecondary?.trim()?.takeIf { it.isNotBlank() }?.let { append(",$it") }
        }
        val values = mutableListOf(
            listOf("${target.basePath}.AddressingType", "Static", "xsd:string"),
            listOf("${target.basePath}.ExternalIPAddress", network.ipAddress.trim(), "xsd:string"),
            listOf("${target.basePath}.SubnetMask", network.subnetMask.trim(), "xsd:string"),
            listOf("${target.basePath}.DefaultGateway", network.gateway.trim(), "xsd:string"),
            listOf("${target.basePath}.DNSServers", dnsServers, "xsd:string"),
        )
        val warnings = mutableListOf<String>()
        network.vlanId?.let { vlanId ->
            val vlanPath = findWritableVlanParameter(target.connectionDeviceNode)
            if (vlanPath == null) {
                warnings += CpeWarnings.VLAN_NOT_WRITABLE
            } else {
                values += listOf(vlanPath, vlanId.toString(), "xsd:unsignedInt")
            }
        }
        return WanDiscovery(values, warnings)
    }

    private fun findPrimaryWanIpConnection(wanDevice: JsonNode?): WanIpConnectionTarget? {
        if (wanDevice == null || wanDevice.isMissingNode || !wanDevice.isObject) return null
        wanDevice.fields().forEach { (wanIndex, deviceNode) ->
            if (wanIndex.startsWith("_") || !deviceNode.isObject) return@forEach
            val connectionDevices = deviceNode.path("WANConnectionDevice")
            if (!connectionDevices.isObject) return@forEach
            connectionDevices.fields().forEach { (connIndex, connectionDeviceNode) ->
                if (connIndex.startsWith("_") || !connectionDeviceNode.isObject) return@forEach
                val ipConnections = connectionDeviceNode.path("WANIPConnection")
                if (!ipConnections.isObject) return@forEach
                ipConnections.fields().forEach { (ipIndex, connectionNode) ->
                    if (ipIndex.startsWith("_") || !connectionNode.isObject) return@forEach
                    val externalIp = connectionNode.path("ExternalIPAddress")
                    val writable = !externalIp.path("_writable").isBoolean ||
                        externalIp.path("_writable").asBoolean()
                    if (!writable) return@forEach
                    val objectPath = externalIp.path("_object").asText("").removeSuffix(".ExternalIPAddress")
                    val basePath = objectPath.ifBlank {
                        "InternetGatewayDevice.WANDevice.$wanIndex.WANConnectionDevice.$connIndex.WANIPConnection.$ipIndex"
                    }
                    return WanIpConnectionTarget(
                        basePath = basePath,
                        connectionDeviceNode = connectionDeviceNode,
                    )
                }
            }
        }
        return null
    }

    private fun findWritableVlanParameter(connectionDeviceNode: JsonNode): String? {
        if (!connectionDeviceNode.isObject) return null
        val candidates = mutableListOf<Pair<Int, String>>()
        connectionDeviceNode.fields().forEach { (name, node) ->
            if (name.startsWith("_") || !node.isObject) return@forEach
            if (!name.contains("vlan", ignoreCase = true)) return@forEach
            val writable = !node.path("_writable").isBoolean || node.path("_writable").asBoolean()
            if (!writable) return@forEach
            val objectPath = node.path("_object").asText("").takeIf { it.isNotBlank() }
            if (objectPath != null) {
                val priority = when {
                    name.equals("X_VSOL_VLANID", ignoreCase = true) -> 0
                    name.contains("VLANIDMark", ignoreCase = true) -> 1
                    else -> 2
                }
                candidates += priority to objectPath
            }
        }
        return candidates.minByOrNull { it.first }?.second
    }

    private fun discoverWifiParameterValues(
        sn: String,
        ssid: String,
        passphrase: String,
    ): List<List<String>> {
        val query = objectMapper.writeValueAsString(serialLookupQuery(sn))
        val devices = webClient.get()
            .uri(
                "/devices/?query={query}&projection={projection}",
                query,
                "InternetGatewayDevice.LANDevice.1.WLANConfiguration",
            )
            .retrieve()
            .bodyToMono(object : ParameterizedTypeReference<List<JsonNode>>() {})
            .block()
            .orEmpty()
        val wlan = devices.firstOrNull()
            ?.path("InternetGatewayDevice")
            ?.path("LANDevice")
            ?.path("1")
            ?.path("WLANConfiguration")
        val instances = wifiInstancesWithPassword(wlan)
        val targets = if (instances.isEmpty()) listOf("1") else instances
        return targets.flatMap { index ->
            val base = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.$index"
            listOf(
                listOf("$base.SSID", ssid, "xsd:string"),
                listOf("$base.KeyPassphrase", passphrase, "xsd:string"),
            )
        }
    }

    private fun wifiInstancesWithPassword(wlan: JsonNode?): List<String> {
        if (wlan == null || wlan.isMissingNode || !wlan.isObject) return emptyList()
        val indexes = mutableListOf<String>()
        wlan.fields().forEach { (index, config) ->
            if (index.startsWith("_")) return@forEach
            val key = config.path("KeyPassphrase")
            val writable = !key.path("_writable").isBoolean || key.path("_writable").asBoolean()
            val current = key.path("_value").asText("")
            if (writable && current.isNotBlank()) {
                indexes += index
            }
        }
        return indexes.sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }
    }

    private data class WanDiscovery(
        val parameterValues: List<List<String>>,
        val warnings: List<String>,
    )

    private data class WanIpConnectionTarget(
        val basePath: String,
        val connectionDeviceNode: JsonNode,
    )

    companion object {
        val DEFAULT_MAX_INFORM_AGE: Duration = Duration.ofMinutes(20)
        private const val DEVICE_ID_SEPARATOR = "-"
    }
}
