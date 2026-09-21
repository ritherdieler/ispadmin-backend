package com.dscorp.wispadmin.acs.genieacs

import com.dscorp.wispadmin.acs.CpeStatus
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class VparamProvisioner(
    private val client: GenieAcsClient,
    private val properties: GenieAcsProperties,
    private val objectMapper: ObjectMapper = ObjectMapper(),
) {
    private val log = LoggerFactory.getLogger(VparamProvisioner::class.java)

    @Volatile
    private var clock: () -> Long = { System.currentTimeMillis() }

    @Volatile
    private var sleeper: (Long) -> Unit = { ms -> if (ms > 0) Thread.sleep(ms) }

    internal fun withTimeControls(clock: () -> Long, sleeper: (Long) -> Unit): VparamProvisioner {
        this.clock = clock
        this.sleeper = sleeper
        return this
    }

    fun provision(request: Tr069ProvisionRequest): Tr069ProvisionOutcome {
        if (!properties.enabled) {
            return Tr069ProvisionOutcome(status = CpeStatus.NA)
        }
        val ip = request.ip?.trim().orEmpty()
        val hasWifi = !request.wifiSsid24.isNullOrBlank() || !request.wifiSsid5.isNullOrBlank()
        val wifiOnly = request.identityOnly || (!request.usesPppoe() && ip.isBlank() && hasWifi)
        if (!wifiOnly && !request.usesPppoe() && ip.isBlank()) {
            return Tr069ProvisionOutcome(
                status = CpeStatus.FAILED,
                error = "Faltan IP o credenciales PPPoE para aprovisionar WAN.",
                message = "Faltan IP o credenciales PPPoE para aprovisionar WAN.",
            )
        }

        val waitTimeout = request.waitTimeoutMs ?: properties.waitTimeoutMs
        val findDeadline = clock() + waitTimeout
        var lastMatch: Tr069SerialMatch = Tr069SerialMatch.None(
            Tr069SerialMatcher.normalizeSuffix(request.onuSerial).orEmpty(),
        )
        while (clock() <= findDeadline) {
            val devices = try {
                client.listDevices()
            } catch (ex: Exception) {
                log.warn("Error listing GenieACS devices: {}", ex.message)
                sleeper(properties.pollIntervalMs)
                continue
            }
            lastMatch = Tr069SerialMatcher.findUnique(request.onuSerial, devices)
            when (lastMatch) {
                is Tr069SerialMatch.Found -> break
                is Tr069SerialMatch.Ambiguous -> {
                    return failed("Más de un CPE en GenieACS coincide con el sufijo del serial (${lastMatch.suffix}).")
                }
                is Tr069SerialMatch.InvalidSerial -> {
                    return failed("Serial de ONU inválido para correlación TR-069.")
                }
                is Tr069SerialMatch.None -> sleeper(properties.pollIntervalMs)
            }
        }
        val found = lastMatch as? Tr069SerialMatch.Found
            ?: return failed("La ONU no contactó al ACS dentro del tiempo de espera.")
        val device = found.device
        val baseSnapshot = snapshotFromDevice(device, request)
        try {
            client.purgeDeviceQueue(device.id)
        } catch (ex: Exception) {
            log.warn("Could not purge GenieACS queue for {}: {}", device.id, ex.message)
        }

        val connectionName = request.connectionName?.takeIf { it.isNotBlank() }
            ?: properties.clientWanNamePattern.replace("{vlan}", request.wanVlanId.toString())
        val applyDeadline = clock() + waitTimeout

        if (request.usesPppoe()) {
            val payload = linkedMapOf(
                "username" to request.pppoeUsername,
                "password" to request.pppoePassword,
                "vlanId" to request.wanVlanId,
                "connectionName" to connectionName,
            )
            applyPppoeUntilSettled(device.id, payload, baseSnapshot)?.let { return it }
        } else if (!wifiOnly) {
            val segment = request.ipSegment?.trim().orEmpty()
            val payload = linkedMapOf(
                "ip" to ip,
                "subnetMask" to GenieAcsValues.cidrToSubnetMask(segment.ifBlank { "/24" }),
                "gateway" to (if (segment.isBlank()) "" else segment.getBaseIpFromRange() + "1"),
                "dns" to properties.defaultDns,
                "vlanId" to request.wanVlanId,
                "connectionName" to connectionName,
            )
            spvJson(device.id, GfVirtualParameters.APPLY_INTERNET_STATIC, payload, baseSnapshot)?.let { return it }
        }

        if (hasWifi) {
            val payload = linkedMapOf<String, String?>(
                "ssid24" to request.wifiSsid24,
                "password24" to request.wifiPassword24,
                "ssid5" to request.wifiSsid5,
                "password5" to request.wifiPassword5,
            )
            spvJson(device.id, GfVirtualParameters.SET_WIFI, payload, baseSnapshot)?.let { return it }
        }

        val statusParams = buildList {
            if (!wifiOnly) add(GfVirtualParameters.INTERNET_STATUS)
            if (hasWifi) add(GfVirtualParameters.WIFI_STATUS)
        }
        if (statusParams.isNotEmpty()) {
            client.getParameterValues(device.id, statusParams, connectionRequest = true)
        }

        var lastInternet: JsonNode? = null
        var lastWifi: JsonNode? = null
        while (clock() <= applyDeadline) {
            lastInternet = if (wifiOnly) null else readJson(device.id, GfVirtualParameters.INTERNET_STATUS)
            lastWifi = if (hasWifi) readJson(device.id, GfVirtualParameters.WIFI_STATUS) else null
            val wanOk = wifiOnly || internetSatisfied(request, ip, lastInternet)
            val wifiOk = !hasWifi || wifiSatisfied(request, lastWifi)
            if (wanOk && wifiOk) {
                return Tr069ProvisionOutcome(
                    status = CpeStatus.COMPLETE,
                    deviceId = device.id,
                    message = "ONU configurada automáticamente por TR-069.",
                    acsSnapshot = baseSnapshot.copy(
                        wanIpCache = lastInternet?.path("ip")?.asText()?.takeIf { it.isNotBlank() } ?: ip.ifBlank { null },
                        ssid24 = request.wifiSsid24,
                        ssid5 = request.wifiSsid5,
                    ),
                )
            }
            sleeper(properties.pollIntervalMs)
            if (clock() <= applyDeadline && statusParams.isNotEmpty()) {
                client.getParameterValues(device.id, statusParams, connectionRequest = true)
            }
        }
        return Tr069ProvisionOutcome(
            status = CpeStatus.PENDING,
            deviceId = device.id,
            error = "IP/SSID/WAN ConnectionStatus no se confirmaron en el ACS dentro del tiempo de espera.",
            message = "IP/SSID/WAN ConnectionStatus no se confirmaron en el ACS dentro del tiempo de espera.",
            acsSnapshot = baseSnapshot,
        )
    }

    private fun internetSatisfied(request: Tr069ProvisionRequest, expectedIp: String, node: JsonNode?): Boolean {
        if (node == null) return false
        val connected = node.path("connected").asBoolean(false)
        val observedIp = node.path("ip").asText(null)?.takeIf { it.isNotBlank() }
        val ipOk = if (request.usesPppoe()) {
            observedIp != null && observedIp.startsWith("10.64.")
        } else {
            Tr069WanVerification.ipSatisfied(expectedIp, observedIp, false)
        }
        return connected && ipOk
    }

    private fun wifiSatisfied(request: Tr069ProvisionRequest, node: JsonNode?): Boolean {
        if (node == null) return false
        val ssid24Ok = request.wifiSsid24.isNullOrBlank() || node.path("ssid24").asText(null) == request.wifiSsid24
        val ssid5Ok = request.wifiSsid5.isNullOrBlank() || node.path("ssid5").asText(null) == request.wifiSsid5
        return ssid24Ok && ssid5Ok
    }

    private fun applyPppoeUntilSettled(
        deviceId: String,
        payload: Map<String, Any?>,
        baseSnapshot: Tr069AcsSnapshot,
    ): Tr069ProvisionOutcome? {
        var pending: String? = "apply"
        var tries = 0
        while (tries < 3 && pending != null) {
            spvJson(deviceId, GfVirtualParameters.APPLY_INTERNET_PPPOE, payload, baseSnapshot)?.let { return it }
            tries++
            pending = readJson(deviceId, GfVirtualParameters.APPLY_INTERNET_PPPOE)
                ?.path("pending")
                ?.asText()
                ?.takeIf { it.isNotBlank() }
        }
        if (pending != null) {
            val error = "GfApplyInternetPppoe stayed pending ($pending)"
            return Tr069ProvisionOutcome(
                status = CpeStatus.FAILED,
                deviceId = deviceId,
                error = error,
                message = error,
                acsSnapshot = baseSnapshot,
            )
        }
        return null
    }

    private fun readJson(deviceId: String, path: String): JsonNode? {
        val raw = client.getDeviceParameterValue(deviceId, path) ?: return null
        return runCatching { objectMapper.readTree(raw) }.getOrNull()
    }

    private fun spvJson(
        deviceId: String,
        path: String,
        payload: Map<String, Any?>,
        baseSnapshot: Tr069AcsSnapshot,
    ): Tr069ProvisionOutcome? {
        val json = objectMapper.writeValueAsString(payload)
        val values = listOf(Tr069ParameterValue(path, json, "xsd:string"))
        var result = client.setParameterValues(deviceId, values, connectionRequest = true)
        if (result.connectionRequestFailed) {
            result = client.setParameterValues(deviceId, values, connectionRequest = false)
        }
        if (!result.accepted || result.statusCode != 200) {
            val error = result.toErrorDetail()
            return Tr069ProvisionOutcome(
                status = CpeStatus.FAILED,
                deviceId = deviceId,
                error = error,
                message = error,
                acsSnapshot = baseSnapshot,
            )
        }
        return null
    }

    private fun snapshotFromDevice(device: GenieAcsDevice, request: Tr069ProvisionRequest) = Tr069AcsSnapshot(
        serialSuffix = Tr069SerialMatcher.normalizeSuffix(request.onuSerial),
        lastInformAt = GenieAcsValues.parseDateTime(device.lastInform),
        productClass = device.productClass,
        oui = device.oui,
        manufacturer = device.manufacturer,
        connectionRequestUrl = device.connectionRequestUrl,
        softwareVersion = device.softwareVersion,
        hardwareVersion = device.hardwareVersion,
        lastBootAt = GenieAcsValues.parseDateTime(device.lastBoot),
        wanIpCache = request.ip?.trim()?.takeIf { it.isNotBlank() },
        ssid24 = request.wifiSsid24,
        ssid5 = request.wifiSsid5,
    )

    private fun failed(message: String) = Tr069ProvisionOutcome(
        status = CpeStatus.FAILED,
        error = message,
        message = message,
    )
}
