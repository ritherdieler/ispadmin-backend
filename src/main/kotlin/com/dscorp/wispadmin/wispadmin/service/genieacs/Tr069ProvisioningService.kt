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
        val deadline = clock() + waitTimeout
        var lastMatch: Tr069SerialMatch = Tr069SerialMatch.None(
            Tr069SerialMatcher.normalizeSuffix(request.onuSerial).orEmpty()
        )

        while (clock() <= deadline) {
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
        val wanIndices = try {
            client.listWanConnectionIndices(device.id)
        } catch (ex: Exception) {
            log.warn("No se pudo descubrir índice WAN de {}: {}", device.id, ex.message)
            emptyList()
        }
        val wanIndex = Tr069ModelProfiles.resolveWanConnectionIndex(wanIndices)
        val profileForWan = resolvedProfile.withWanConnectionIndex(wanIndex)

        val wifiValues = profileForWan.buildWifiParameterValues(
            wifiSsid24 = request.wifiSsid24,
            wifiPassword24 = request.wifiPassword24,
            wifiSsid5 = request.wifiSsid5,
            wifiPassword5 = request.wifiPassword5,
        )
        var setResult: GenieAcsTaskResult? = null
        if (wifiValues.isNotEmpty()) {
            val (wifiSpv, wifiFailure) = submitSpv(device.id, wifiValues, baseSnapshot)
            if (wifiFailure != null) {
                return wifiFailure
            }
            setResult = wifiSpv!!.result
        }

        val values = profileForWan.buildWanParameterValues(
            ip = ip,
            subnetMask = subnetMask,
            gateway = gateway,
            dns = properties.defaultDns,
            vlanId = request.wanVlanId,
        )

        val (wanSpv, wanFailure) = submitSpv(device.id, values, baseSnapshot)
        if (wanFailure != null) {
            return wanFailure
        }
        setResult = wanSpv!!.result

        val snapshotAfterTask = baseSnapshot.withTask(setResult).copy(
            wanIpCache = ip,
            ssid24 = request.wifiSsid24,
            ssid5 = request.wifiSsid5,
        )

        val ssid24Path = "${profileForWan.wlan24Path}.SSID"
        val ssid5Path = "${profileForWan.wlan5Path}.SSID"
        client.getParameterValues(
            deviceId = device.id,
            parameterNames = listOfNotNull(
                request.wifiSsid24?.let { ssid24Path },
                request.wifiSsid5?.let { ssid5Path },
            ),
            connectionRequest = false,
        )

        while (clock() <= deadline) {
            resolveTaskFault(device.id, setResult!!)?.let { genieError ->
                return Tr069ProvisionOutcome(
                    status = Tr069ProvisionStatus.MANUAL_REQUIRED,
                    deviceId = device.id,
                    error = genieError,
                    message = genieError,
                    acsSnapshot = snapshotAfterTask,
                )
            }

            val ssid24Ok = request.wifiSsid24.isNullOrBlank() ||
                client.getDeviceParameterValue(device.id, ssid24Path) == request.wifiSsid24
            val ssid5Ok = request.wifiSsid5.isNullOrBlank() ||
                client.getDeviceParameterValue(device.id, ssid5Path) == request.wifiSsid5
            if (ssid24Ok && ssid5Ok) {
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
            status = Tr069ProvisionStatus.MANUAL_REQUIRED,
            deviceId = device.id,
            error = genieError,
            message = genieError,
            acsSnapshot = snapshotAfterTask,
        )
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
