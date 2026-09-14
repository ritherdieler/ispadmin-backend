package com.dscorp.wispadmin.acs.genieacs

import com.dscorp.wispadmin.acs.CpeCommandResult
import com.dscorp.wispadmin.acs.CpeStatus
import com.dscorp.wispadmin.transport.RegistrationTiming
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class NamedCpeProvisioner(
    private val client: GenieAcsClient,
    private val properties: GenieAcsProperties,
    private val timing: RegistrationTiming = RegistrationTiming.NOOP,
) {
    private val log = LoggerFactory.getLogger(NamedCpeProvisioner::class.java)

    @Volatile
    private var clock: () -> Long = { System.currentTimeMillis() }

    @Volatile
    private var sleeper: (Long) -> Unit = { ms -> if (ms > 0) Thread.sleep(ms) }

    internal fun withTimeControls(clock: () -> Long, sleeper: (Long) -> Unit): NamedCpeProvisioner {
        this.clock = clock
        this.sleeper = sleeper
        return this
    }

    fun provision(request: Tr069ProvisionRequest): Tr069ProvisionOutcome {
        if (!properties.enabled) {
            return Tr069ProvisionOutcome(status = CpeStatus.NA)
        }
        if (!request.usesPppoe()) {
            return failed("Faltan credenciales PPPoE para aprovisionar WAN.")
        }
        val found = timing.span("acs.genieacs.find-device", mapOf("sn" to request.onuSerial)) {
            waitForDevice(request)
        } ?: return lastFindFailure(request)
        val device = found.device
        val layout = NamedCpeLayouts.of(device.productClass)
            ?: return failed("unsupported productClass=${device.productClass}", device.id, snapshotFromDevice(device, request))
        try {
            client.purgeDeviceQueue(device.id)
        } catch (ex: Exception) {
            log.warn("Could not purge GenieACS queue for {}: {}", device.id, ex.message)
        }
        val connectionName = request.connectionName?.takeIf { it.isNotBlank() }
            ?: properties.clientWanNamePattern.replace("{vlan}", request.wanVlanId.toString())
        val passphrase = request.wifiPassword24
        val args = listOf(
            request.pppoeUsername,
            request.pppoePassword,
            request.wanVlanId.toString(),
            connectionName,
            request.wifiSsid24.orEmpty(),
            passphrase.orEmpty(),
            request.wifiSsid5.orEmpty(),
            passphrase.orEmpty(),
        )
        var result = timing.span("acs.genieacs.enqueue-pppoe", mapOf("sn" to request.onuSerial)) {
            var enqueued = client.enqueueProvisions(device.id, NamedGenieAcsProvisions.PPPOE, args, connectionRequest = true)
            if (enqueued.connectionRequestFailed) {
                enqueued = client.enqueueProvisions(device.id, NamedGenieAcsProvisions.PPPOE, args, connectionRequest = false)
            }
            enqueued
        }
        if (!result.accepted) {
            val error = result.toErrorDetail()
            return Tr069ProvisionOutcome(
                status = CpeStatus.FAILED,
                deviceId = device.id,
                error = error,
                message = error,
                acsSnapshot = snapshotFromDevice(device, request),
            )
        }
        return timing.span("acs.genieacs.wait-complete", mapOf("sn" to request.onuSerial)) {
            waitForComplete(device, layout, request)
        }
    }

    fun setWifi(sn: String, ssid24: String?, ssid5: String?, passphrase: String): CpeCommandResult {
        if (passphrase.length < NamedGenieAcsProvisions.MIN_WIFI_PASSPHRASE) {
            return CpeCommandResult(false, CpeStatus.FAILED, "WiFi passphrase shorter than 8")
        }
        if (!properties.enabled) {
            return CpeCommandResult(false, CpeStatus.NA, "ACS disabled")
        }
        val request = Tr069ProvisionRequest(
            onuSerial = sn,
            onuTypeName = null,
            ip = null,
            ipSegment = null,
            wifiSsid24 = ssid24,
            wifiPassword24 = passphrase,
            wifiSsid5 = ssid5,
            wifiPassword5 = passphrase,
            wanVlanId = 1,
        )
        val found = waitForDevice(request) ?: return CpeCommandResult(
            false,
            CpeStatus.FAILED,
            lastFindFailure(request).message,
        )
        val device = found.device
        val layout = NamedCpeLayouts.of(device.productClass)
            ?: return CpeCommandResult(false, CpeStatus.FAILED, "unsupported productClass=${device.productClass}")
        var result = client.enqueueProvisions(
            device.id,
            NamedGenieAcsProvisions.WIFI,
            listOf(ssid24.orEmpty(), ssid5.orEmpty(), passphrase),
            connectionRequest = true,
        )
        if (result.connectionRequestFailed) {
            result = client.enqueueProvisions(
                device.id,
                NamedGenieAcsProvisions.WIFI,
                listOf(ssid24.orEmpty(), ssid5.orEmpty(), passphrase),
                connectionRequest = false,
            )
        }
        if (!result.accepted) {
            return CpeCommandResult(false, CpeStatus.FAILED, result.toErrorDetail())
        }
        val complete = waitForWifi(device.id, layout, ssid24, ssid5, request.waitTimeoutMs ?: properties.waitTimeoutMs)
        return if (complete) {
            CpeCommandResult(true, CpeStatus.COMPLETE, "WiFi aplicado")
        } else {
            CpeCommandResult(true, CpeStatus.PENDING, "SSID no se confirmaron en el ACS dentro del tiempo de espera.")
        }
    }

    private fun waitForComplete(
        device: GenieAcsDevice,
        layout: NamedCpeLayout,
        request: Tr069ProvisionRequest,
    ): Tr069ProvisionOutcome {
        val hasWifi = !request.wifiSsid24.isNullOrBlank() || !request.wifiSsid5.isNullOrBlank()
        val params = buildList {
            add(layout.pppExternalIp)
            if (hasWifi) {
                add(layout.ssid24)
                add(layout.ssid5)
            }
        }
        client.getParameterValues(device.id, params, connectionRequest = true)
        val deadline = clock() + (request.waitTimeoutMs ?: properties.waitTimeoutMs)
        var lastIp: String? = null
        var lastSsid24: String? = null
        var lastSsid5: String? = null
        while (clock() <= deadline) {
            lastIp = client.getDeviceParameterValue(device.id, layout.pppExternalIp)
            lastSsid24 = if (hasWifi) client.getDeviceParameterValue(device.id, layout.ssid24) else null
            lastSsid5 = if (hasWifi) client.getDeviceParameterValue(device.id, layout.ssid5) else null
            val wanOk = lastIp != null && lastIp.startsWith("10.64.")
            val wifiOk = !hasWifi || wifiSatisfied(request, lastSsid24, lastSsid5)
            if (wanOk && wifiOk) {
                return Tr069ProvisionOutcome(
                    status = CpeStatus.COMPLETE,
                    deviceId = device.id,
                    message = "ONU configurada automáticamente por TR-069.",
                    acsSnapshot = snapshotFromDevice(device, request).copy(wanIpCache = lastIp),
                )
            }
            sleeper(properties.pollIntervalMs)
            if (clock() <= deadline) {
                client.getParameterValues(device.id, params, connectionRequest = true)
            }
        }
        return Tr069ProvisionOutcome(
            status = CpeStatus.PENDING,
            deviceId = device.id,
            error = "IP/SSID/WAN ConnectionStatus no se confirmaron en el ACS dentro del tiempo de espera.",
            message = "IP/SSID/WAN ConnectionStatus no se confirmaron en el ACS dentro del tiempo de espera.",
            acsSnapshot = snapshotFromDevice(device, request).copy(wanIpCache = lastIp),
        )
    }

    private fun waitForWifi(
        deviceId: String,
        layout: NamedCpeLayout,
        ssid24: String?,
        ssid5: String?,
        waitTimeoutMs: Long,
    ): Boolean {
        val params = listOf(layout.ssid24, layout.ssid5)
        client.getParameterValues(deviceId, params, connectionRequest = true)
        val deadline = clock() + waitTimeoutMs
        while (clock() <= deadline) {
            val observed24 = client.getDeviceParameterValue(deviceId, layout.ssid24)
            val observed5 = client.getDeviceParameterValue(deviceId, layout.ssid5)
            val ok24 = ssid24.isNullOrBlank() || observed24 == ssid24
            val ok5 = ssid5.isNullOrBlank() || observed5 == ssid5
            if (ok24 && ok5) return true
            sleeper(properties.pollIntervalMs)
            if (clock() <= deadline) {
                client.getParameterValues(deviceId, params, connectionRequest = true)
            }
        }
        return false
    }

    private fun wifiSatisfied(request: Tr069ProvisionRequest, ssid24: String?, ssid5: String?): Boolean {
        val ok24 = request.wifiSsid24.isNullOrBlank() || ssid24 == request.wifiSsid24
        val ok5 = request.wifiSsid5.isNullOrBlank() || ssid5 == request.wifiSsid5
        return ok24 && ok5
    }

    private fun waitForDevice(request: Tr069ProvisionRequest): Tr069SerialMatch.Found? {
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
            when (val match = lastMatch) {
                is Tr069SerialMatch.Found -> return match
                is Tr069SerialMatch.Ambiguous -> {
                    cachedFindFailure =
                        failed("Más de un CPE en GenieACS coincide con el sufijo del serial (${match.suffix}).")
                    return null
                }
                is Tr069SerialMatch.InvalidSerial -> {
                    cachedFindFailure = failed("Serial de ONU inválido para correlación TR-069.")
                    return null
                }
                is Tr069SerialMatch.None -> sleeper(properties.pollIntervalMs)
            }
        }
        cachedFindFailure = when (lastMatch) {
            is Tr069SerialMatch.Ambiguous ->
                failed("Más de un CPE en GenieACS coincide con el sufijo del serial (${lastMatch.suffix}).")
            is Tr069SerialMatch.InvalidSerial ->
                failed("Serial de ONU inválido para correlación TR-069.")
            else -> failed("La ONU no contactó al ACS dentro del tiempo de espera.")
        }
        return null
    }

    @Volatile
    private var cachedFindFailure: Tr069ProvisionOutcome? = null

    private fun lastFindFailure(request: Tr069ProvisionRequest): Tr069ProvisionOutcome {
        return cachedFindFailure ?: failed("La ONU no contactó al ACS dentro del tiempo de espera.")
    }

    private fun snapshotFromDevice(device: GenieAcsDevice, request: Tr069ProvisionRequest) = Tr069AcsSnapshot(
        serialSuffix = Tr069SerialMatcher.normalizeSuffix(request.onuSerial),
        lastInformAt = Tr069ProvisioningService.parseGenieAcsDateTime(device.lastInform),
        productClass = device.productClass,
        oui = device.oui,
        manufacturer = device.manufacturer,
        connectionRequestUrl = device.connectionRequestUrl,
        softwareVersion = device.softwareVersion,
        hardwareVersion = device.hardwareVersion,
        lastBootAt = Tr069ProvisioningService.parseGenieAcsDateTime(device.lastBoot),
        wanIpCache = request.ip?.trim()?.takeIf { it.isNotBlank() },
        ssid24 = request.wifiSsid24,
        ssid5 = request.wifiSsid5,
    )

    private fun failed(message: String, deviceId: String? = null, snapshot: Tr069AcsSnapshot? = null) =
        Tr069ProvisionOutcome(
            status = CpeStatus.FAILED,
            deviceId = deviceId,
            error = message,
            message = message,
            acsSnapshot = snapshot,
        )
}
