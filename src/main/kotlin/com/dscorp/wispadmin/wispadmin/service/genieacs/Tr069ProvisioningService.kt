package com.dscorp.wispadmin.wispadmin.service.genieacs

import com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus
import com.dscorp.wispadmin.wispadmin.extensions.getBaseIpFromRange
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

data class Tr069ProvisionRequest(
    val onuSerial: String?,
    val onuTypeName: String?,
    val ip: String?,
    val ipSegment: String?,
    val wifiSsid24: String?,
    val wifiPassword24: String?,
    val wifiSsid5: String?,
    val wifiPassword5: String?,
    val waitTimeoutMs: Long? = null,
    /** WAN VLAN from app (`subscription.vlan`). Required when GenieACS is enabled. */
    val wanVlanId: Int,
)

data class Tr069AcsSnapshot(
    val serialSuffix: String? = null,
    val lastInformAt: LocalDateTime? = null,
    val productClass: String? = null,
    val oui: String? = null,
    val manufacturer: String? = null,
    val connectionRequestUrl: String? = null,
    val softwareVersion: String? = null,
    val hardwareVersion: String? = null,
    val lastBootAt: LocalDateTime? = null,
    val wanIpCache: String? = null,
    val ssid24: String? = null,
    val ssid5: String? = null,
    val lastTaskId: String? = null,
    val lastTaskStatus: String? = null,
    val lastTaskAt: LocalDateTime? = null,
)

data class Tr069ProvisionOutcome(
    val status: Tr069ProvisionStatus,
    val deviceId: String? = null,
    val error: String? = null,
    val message: String? = null,
    val acsSnapshot: Tr069AcsSnapshot? = null,
)

@Service
class Tr069ProvisioningService(
    private val client: GenieAcsClient,
    private val properties: GenieAcsProperties,
    private val profileRegistry: Tr069ModelProfileRegistry,
) {
    private val log = LoggerFactory.getLogger(Tr069ProvisioningService::class.java)

    @Volatile
    private var clock: () -> Long = { System.currentTimeMillis() }

    @Volatile
    private var sleeper: (Long) -> Unit = { ms -> if (ms > 0) Thread.sleep(ms) }

    /** Solo para tests unitarios (avance de reloj sin sleep real). */
    internal fun withTimeControls(clock: () -> Long, sleeper: (Long) -> Unit): Tr069ProvisioningService {
        this.clock = clock
        this.sleeper = sleeper
        return this
    }

    fun provision(request: Tr069ProvisionRequest): Tr069ProvisionOutcome {
        if (!properties.enabled) {
            return Tr069ProvisionOutcome(
                status = Tr069ProvisionStatus.NA,
                message = null,
            )
        }

        val ip = request.ip?.trim().orEmpty()
        val segment = request.ipSegment?.trim().orEmpty()
        if (ip.isBlank() || segment.isBlank()) {
            return manual("Faltan IP o segmento del pool para aprovisionar WAN por TR-069.")
        }

        val waitTimeout = request.waitTimeoutMs ?: properties.waitTimeoutMs
        val findDeadline = clock() + waitTimeout
        var lastMatch: Tr069SerialMatch = Tr069SerialMatch.None(
            Tr069SerialMatcher.normalizeSuffix(request.onuSerial).orEmpty()
        )

        while (clock() <= findDeadline) {
            val devices = try {
                client.listDevices()
            } catch (ex: Exception) {
                log.warn("Error listando devices GenieACS: {}", ex.message)
                sleeper(properties.pollIntervalMs)
                continue
            }
            lastMatch = Tr069SerialMatcher.findUnique(request.onuSerial, devices)
            when (lastMatch) {
                is Tr069SerialMatch.Found -> break
                is Tr069SerialMatch.Ambiguous -> {
                    return manual(
                        "Más de un CPE en GenieACS coincide con el sufijo del serial (${lastMatch.suffix})."
                    )
                }
                is Tr069SerialMatch.InvalidSerial -> {
                    return manual("Serial de ONU inválido para correlación TR-069.")
                }
                is Tr069SerialMatch.None -> {
                    sleeper(properties.pollIntervalMs)
                }
            }
        }

        val found = lastMatch as? Tr069SerialMatch.Found
            ?: return manual(
                "La ONU no contactó al ACS dentro del tiempo de espera. Configure la ONU manualmente."
            )

        val device = found.device
        val baseSnapshot = snapshotFromDevice(device, request)

        try {
            client.purgeDeviceQueue(device.id)
        } catch (ex: Exception) {
            log.warn("No se pudo purgar cola GenieACS de {}: {}", device.id, ex.message)
        }

        if (!profileRegistry.hasImportedProfiles()) {
            return Tr069ProvisionOutcome(
                status = Tr069ProvisionStatus.MANUAL_REQUIRED,
                deviceId = device.id,
                error = MISSING_IMPORTED_PROFILES_MESSAGE,
                message = MISSING_IMPORTED_PROFILES_MESSAGE,
                acsSnapshot = baseSnapshot,
            )
        }

        val resolvedProfile = Tr069ModelProfiles.resolve(
            onuTypeName = request.onuTypeName,
            productClass = device.productClass,
        ) ?: run {
            val modelLabel = device.productClass ?: request.onuTypeName ?: "desconocido"
            val message = missingModelProfileMessage(modelLabel)
            return Tr069ProvisionOutcome(
                status = Tr069ProvisionStatus.MANUAL_REQUIRED,
                deviceId = device.id,
                error = message,
                message = message,
                acsSnapshot = baseSnapshot,
            )
        }

        val gateway = segment.getBaseIpFromRange() + "1"
        val subnetMask = cidrToSubnetMask(segment)
        val profileForClientWan = resolvedProfile.forClientInternetWan(properties.clientWanIndex)
        val applyDeadline = clock() + waitTimeout

        ensureClientWanSlot(device.id, profileForClientWan, baseSnapshot, applyDeadline)?.let { return it }

        val connectionName = properties.clientWanNamePattern.replace("{vlan}", request.wanVlanId.toString())
        val wanValues = profileForClientWan.buildClientInternetWanParameterValues(
            ip = ip,
            subnetMask = subnetMask,
            gateway = gateway,
            dns = properties.defaultDns,
            vlanId = request.wanVlanId,
            connectionName = connectionName,
        )
        val wifiValues = profileForClientWan.buildWifiParameterValues(
            wifiSsid24 = request.wifiSsid24,
            wifiPassword24 = request.wifiPassword24,
            wifiSsid5 = request.wifiSsid5,
            wifiPassword5 = request.wifiPassword5,
        )
        val allValues = wanValues + wifiValues
        var (spv, spvFailure) = submitSpv(device.id, allValues, baseSnapshot)
        if (spvFailure != null && isMissingParameter(spvFailure.error)) {
            log.warn(
                "SPV 9005/parámetro inválido en {}; recreando WANIP y reintentando",
                device.id,
            )
            recreateClientWanIp(device.id, profileForClientWan, baseSnapshot, applyDeadline)?.let { return it }
            val retry = submitSpv(device.id, allValues, baseSnapshot)
            spv = retry.first
            spvFailure = retry.second
        }
        if (spvFailure != null) {
            return spvFailure
        }

        val lastResult = spv!!.result
        val snapshotAfterTask = baseSnapshot.withTask(lastResult).copy(
            wanIpCache = ip,
            ssid24 = request.wifiSsid24,
            ssid5 = request.wifiSsid5,
        )

        val wanIpPath = "${profileForClientWan.wanIpConnectionPath}.ExternalIPAddress"
        val ssid24Path = "${profileForClientWan.wlan24Path}.SSID"
        val ssid5Path = "${profileForClientWan.wlan5Path}.SSID"
        client.getParameterValues(
            deviceId = device.id,
            parameterNames = listOfNotNull(
                wanIpPath,
                request.wifiSsid24?.let { ssid24Path },
                request.wifiSsid5?.let { ssid5Path },
            ),
            connectionRequest = true,
        )

        while (clock() <= applyDeadline) {
            resolveTaskFault(device.id, lastResult)?.let { genieError ->
                return Tr069ProvisionOutcome(
                    status = Tr069ProvisionStatus.MANUAL_REQUIRED,
                    deviceId = device.id,
                    error = genieError,
                    message = genieError,
                    acsSnapshot = snapshotAfterTask,
                )
            }

            val ipOk = client.getDeviceParameterValue(device.id, wanIpPath) == ip
            val ssid24Ok = request.wifiSsid24.isNullOrBlank() ||
                client.getDeviceParameterValue(device.id, ssid24Path) == request.wifiSsid24
            val ssid5Ok = request.wifiSsid5.isNullOrBlank() ||
                client.getDeviceParameterValue(device.id, ssid5Path) == request.wifiSsid5
            if (ipOk && ssid24Ok && ssid5Ok) {
                return Tr069ProvisionOutcome(
                    status = Tr069ProvisionStatus.COMPLETE,
                    deviceId = device.id,
                    message = "ONU configurada automáticamente por TR-069.",
                    acsSnapshot = snapshotAfterTask,
                )
            }
            sleeper(properties.pollIntervalMs)
        }

        val genieError = SSID_VERIFICATION_TIMEOUT_MESSAGE
        return Tr069ProvisionOutcome(
            status = Tr069ProvisionStatus.PENDING,
            deviceId = device.id,
            error = genieError,
            message = genieError,
            acsSnapshot = snapshotAfterTask,
        )
    }

    private fun ensureClientWanSlot(
        deviceId: String,
        profile: Tr069ModelProfile,
        baseSnapshot: Tr069AcsSnapshot,
        applyDeadline: Long,
    ): Tr069ProvisionOutcome? {
        val wcdParent = profile.wcdParentPath()
        val clientWanIndex = profile.clientWanSlotIndex()
        val wanIpInstance = profile.wanIpInstanceIndex()
        refreshWanConnectionTree(deviceId, wcdParent, baseSnapshot)?.let { return it }
        val slots = try {
            client.listWanConnectionDeviceIndices(deviceId, wcdParent)
        } catch (ex: Exception) {
            log.warn("No se pudo listar WANConnectionDevice de {}: {}", deviceId, ex.message)
            emptyList()
        }
        if (clientWanIndex !in slots) {
            log.info("ONU {} sin WCD.{}; AddObject WANConnectionDevice", deviceId, clientWanIndex)
            submitAddObject(deviceId, wcdParent, baseSnapshot)?.let { failure ->
                val recovered = isResourcesExceeded(failure.error) &&
                    clientWanIndex in client.listWanConnectionDeviceIndices(deviceId, wcdParent)
                if (!recovered) return failure
            }
            sleeper(properties.pollIntervalMs)
        }
        if (!client.hasWanIpConnection(deviceId, clientWanIndex, wcdParent, wanIpInstance)) {
            log.info(
                "ONU {} WCD.{} sin WANIPConnection.{}; AddObject WANIP",
                deviceId,
                clientWanIndex,
                wanIpInstance,
            )
            submitAddObject(
                deviceId,
                "$wcdParent.$clientWanIndex.WANIPConnection",
                baseSnapshot,
            )?.let { failure ->
                val recovered = isResourcesExceeded(failure.error) &&
                    client.hasWanIpConnection(deviceId, clientWanIndex, wcdParent, wanIpInstance)
                if (!recovered) return failure
            }
            waitUntilHasWanIp(deviceId, profile, applyDeadline)
        }
        return null
    }

    private fun recreateClientWanIp(
        deviceId: String,
        profile: Tr069ModelProfile,
        baseSnapshot: Tr069AcsSnapshot,
        applyDeadline: Long,
    ): Tr069ProvisionOutcome? {
        val wcdParent = profile.wcdParentPath()
        val clientWanIndex = profile.clientWanSlotIndex()
        val wanIpInstance = profile.wanIpInstanceIndex()
        submitAddObject(
            deviceId,
            "$wcdParent.$clientWanIndex.WANIPConnection",
            baseSnapshot,
        )?.let { failure ->
            val recovered = isResourcesExceeded(failure.error) &&
                client.hasWanIpConnection(deviceId, clientWanIndex, wcdParent, wanIpInstance)
            if (!recovered) return failure
        }
        waitUntilHasWanIp(deviceId, profile, applyDeadline)
        return null
    }

    private fun waitUntilHasWanIp(
        deviceId: String,
        profile: Tr069ModelProfile,
        applyDeadline: Long,
    ) {
        val wcdParent = profile.wcdParentPath()
        val clientWanIndex = profile.clientWanSlotIndex()
        val wanIpInstance = profile.wanIpInstanceIndex()
        while (clock() <= applyDeadline) {
            if (client.hasWanIpConnection(deviceId, clientWanIndex, wcdParent, wanIpInstance)) {
                return
            }
            sleeper(properties.pollIntervalMs)
        }
    }

    /**
     * ACS Mongo puede conservar un WCD.2 de un alta anterior aunque el CPE lo haya
     * perdido al reautorizar en OLT. Sin refresh, [listWanConnectionDeviceIndices]
     * salta el AddObject y el SPV va a un slot fantasma.
     */
    private fun refreshWanConnectionTree(
        deviceId: String,
        wcdParent: String,
        baseSnapshot: Tr069AcsSnapshot,
    ): Tr069ProvisionOutcome? {
        log.info("ONU {} refreshObject {}", deviceId, wcdParent)
        var result = client.refreshObject(deviceId, wcdParent, connectionRequest = true)
        if (result.connectionRequestFailed) {
            log.warn(
                "Connection Request falló para refreshObject {}; reintentando sin connection_request",
                deviceId,
            )
            result = client.refreshObject(deviceId, wcdParent, connectionRequest = false)
            if (result.connectionRequestFailed || !result.accepted) {
                log.warn("refreshObject de {} falló; se usa caché ACS: {}", deviceId, result.toErrorDetail())
                return null
            }
        } else if (!result.accepted) {
            log.warn("refreshObject de {} no aceptado; se usa caché ACS: {}", deviceId, result.toErrorDetail())
            return null
        }
        resolveTaskFault(deviceId, result)?.let { genieError ->
            return Tr069ProvisionOutcome(
                status = Tr069ProvisionStatus.MANUAL_REQUIRED,
                deviceId = deviceId,
                error = genieError,
                message = genieError,
                acsSnapshot = baseSnapshot.withTask(result),
            )
        }
        return null
    }

    private fun submitAddObject(
        deviceId: String,
        objectName: String,
        baseSnapshot: Tr069AcsSnapshot,
    ): Tr069ProvisionOutcome? {
        var result = client.addObject(deviceId, objectName, connectionRequest = true)
        if (result.connectionRequestFailed) {
            log.warn(
                "Connection Request falló para AddObject {}; reintentando sin connection_request",
                deviceId,
            )
            result = client.addObject(deviceId, objectName, connectionRequest = false)
            if (result.connectionRequestFailed || !result.accepted) {
                val genieError = result.toErrorDetail()
                return Tr069ProvisionOutcome(
                    status = Tr069ProvisionStatus.MANUAL_REQUIRED,
                    deviceId = deviceId,
                    error = genieError,
                    message = genieError,
                    acsSnapshot = baseSnapshot.withTask(result),
                )
            }
        } else if (!result.accepted) {
            val genieError = result.toErrorDetail()
            return Tr069ProvisionOutcome(
                status = Tr069ProvisionStatus.MANUAL_REQUIRED,
                deviceId = deviceId,
                error = genieError,
                message = genieError,
                acsSnapshot = baseSnapshot.withTask(result),
            )
        }
        resolveTaskFault(deviceId, result)?.let { genieError ->
            return Tr069ProvisionOutcome(
                status = Tr069ProvisionStatus.MANUAL_REQUIRED,
                deviceId = deviceId,
                error = genieError,
                message = genieError,
                acsSnapshot = baseSnapshot.withTask(result),
            )
        }
        return null
    }

    private fun isResourcesExceeded(error: String?): Boolean {
        val text = error.orEmpty()
        return text.contains("9004") || text.contains("Resources exceeded", ignoreCase = true)
    }

    private fun isMissingParameter(error: String?): Boolean {
        val text = error.orEmpty()
        return text.contains("9005") || text.contains("Invalid parameter name", ignoreCase = true)
    }

    private data class SpvSubmission(val result: GenieAcsTaskResult)

    private fun submitSpv(
        deviceId: String,
        values: List<Tr069ParameterValue>,
        baseSnapshot: Tr069AcsSnapshot,
    ): Pair<SpvSubmission?, Tr069ProvisionOutcome?> {
        var setResult = client.setParameterValues(deviceId, values, connectionRequest = true)
        if (setResult.connectionRequestFailed) {
            log.warn(
                "Connection Request falló para {}; reintentando sin connection_request",
                deviceId,
            )
            setResult = client.setParameterValues(deviceId, values, connectionRequest = false)
            if (setResult.connectionRequestFailed || !setResult.accepted) {
                val genieError = setResult.toErrorDetail()
                return null to Tr069ProvisionOutcome(
                    status = Tr069ProvisionStatus.MANUAL_REQUIRED,
                    deviceId = deviceId,
                    error = genieError,
                    message = genieError,
                    acsSnapshot = baseSnapshot.withTask(setResult),
                )
            }
        } else if (!setResult.accepted) {
            val genieError = setResult.toErrorDetail()
            return null to Tr069ProvisionOutcome(
                status = Tr069ProvisionStatus.MANUAL_REQUIRED,
                deviceId = deviceId,
                error = genieError,
                message = genieError,
                acsSnapshot = baseSnapshot.withTask(setResult),
            )
        }

        resolveTaskFault(deviceId, setResult)?.let { genieError ->
            return null to Tr069ProvisionOutcome(
                status = Tr069ProvisionStatus.MANUAL_REQUIRED,
                deviceId = deviceId,
                error = genieError,
                message = genieError,
                acsSnapshot = baseSnapshot.withTask(setResult),
            )
        }

        return SpvSubmission(setResult) to null
    }

    private fun resolveTaskFault(deviceId: String, setResult: GenieAcsTaskResult): String? {
        val taskId = setResult.taskId ?: return null
        val taskFaultBody = client.findFaultBodyForTask(deviceId, taskId) ?: return null
        return GenieAcsClient.formatTaskError(
            GenieAcsTaskResult(
                statusCode = setResult.statusCode,
                body = taskFaultBody,
                accepted = false,
                taskId = taskId,
            )
        )
    }

    private fun snapshotFromDevice(
        device: GenieAcsDevice,
        request: Tr069ProvisionRequest,
    ): Tr069AcsSnapshot = Tr069AcsSnapshot(
        serialSuffix = Tr069SerialMatcher.normalizeSuffix(request.onuSerial)
            ?: Tr069SerialMatcher.normalizeSuffix(device.serialNumber),
        lastInformAt = parseGenieAcsDateTime(device.lastInform),
        productClass = device.productClass,
        oui = device.oui,
        manufacturer = device.manufacturer,
        connectionRequestUrl = device.connectionRequestUrl,
        softwareVersion = device.softwareVersion,
        hardwareVersion = device.hardwareVersion,
        lastBootAt = parseGenieAcsDateTime(device.lastBoot),
        wanIpCache = request.ip?.trim()?.takeIf { it.isNotBlank() },
        ssid24 = request.wifiSsid24,
        ssid5 = request.wifiSsid5,
    )

    private fun Tr069AcsSnapshot.withTask(result: GenieAcsTaskResult): Tr069AcsSnapshot = copy(
        lastTaskId = result.taskId ?: lastTaskId,
        lastTaskStatus = when {
            result.connectionRequestFailed -> "cr_failed"
            result.accepted -> "accepted"
            else -> "rejected"
        },
        lastTaskAt = LocalDateTime.now(ZoneOffset.UTC),
    )

    private fun manual(message: String) = Tr069ProvisionOutcome(
        status = Tr069ProvisionStatus.MANUAL_REQUIRED,
        error = message,
        message = message,
    )

    companion object {
        const val MISSING_IMPORTED_PROFILES_MESSAGE =
            "No hay perfiles TR-069 importados. Configure los perfiles en Administración → Perfiles TR-069 antes de registrar."

        fun missingModelProfileMessage(modelLabel: String): String =
            "Modelo ONU sin perfil TR-069 ($modelLabel). Importe el CSV del modelo en Administración → Perfiles TR-069."

        const val SSID_VERIFICATION_TIMEOUT_MESSAGE =
            "Los SSIDs no se confirmaron en el ACS dentro del tiempo de espera."

        fun cidrToSubnetMask(cidr: String): String {
            val prefix = cidr.substringAfter("/", "24").toIntOrNull()?.coerceIn(0, 32) ?: 24
            val mask = if (prefix == 0) 0 else (-1 shl (32 - prefix))
            return listOf(24, 16, 8, 0).joinToString(".") { shift ->
                ((mask ushr shift) and 0xff).toString()
            }
        }

        fun parseGenieAcsDateTime(raw: String?): LocalDateTime? {
            if (raw.isNullOrBlank()) return null
            return try {
                Instant.parse(raw).atZone(ZoneOffset.UTC).toLocalDateTime()
            } catch (_: DateTimeParseException) {
                try {
                    LocalDateTime.parse(raw)
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }
    }
}
