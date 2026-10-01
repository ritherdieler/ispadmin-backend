package com.dscorp.wispadmin.acs.service

import com.dscorp.wispadmin.acs.OnboardingV2InternetRequest
import com.dscorp.wispadmin.acs.OnboardingV2TaskResponse
import com.dscorp.wispadmin.acs.OnboardingV2WifiRequest
import com.dscorp.wispadmin.acs.OnboardingV2WifiCompensateRequest
import com.dscorp.wispadmin.acs.OnboardingV2WanCleanupRequest
import com.dscorp.wispadmin.acs.genieacs.GenieAcsClient
import com.dscorp.wispadmin.acs.genieacs.OnboardingV2CpeLayouts
import com.dscorp.wispadmin.acs.genieacs.Tr069SerialMatcher
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.slf4j.LoggerFactory
import org.springframework.web.server.ResponseStatusException

internal const val SESSION_RETRY_STATUS = "SESSION_RETRY"

internal enum class SessionDropDecision { RETRY, TERMINAL }

internal fun wanStatusNeedsRefresh(compensation: Boolean): Boolean = !compensation

internal fun onboardingOwnerMarker(operationId: String): String =
    "GFv2-" + operationId.filter { it.isLetterOrDigit() }.take(27)

internal fun sessionDropDecision(message: String?, alreadyRetried: Boolean): SessionDropDecision =
    if (message == "The TR-069 session was unsuccessfully terminated" && !alreadyRetried) {
        SessionDropDecision.RETRY
    } else {
        SessionDropDecision.TERMINAL
    }

internal fun onboardingFaultReason(body: String?): String? {
    if (body.isNullOrBlank()) return null
    val detail = onboardingFaultParameterDetail(body)
    if (!detail.isNullOrBlank()) return detail
    val message = runCatching { ObjectMapper().readTree(body).path("message").asText("") }.getOrDefault("").trim()
    return message.takeIf { it.isNotEmpty() }?.take(180)
}

internal fun onboardingFaultParameterDetail(body: String?): String? {
    if (body.isNullOrBlank()) return null
    return runCatching {
        val faults = ObjectMapper().readTree(body).path("detail").path("setParameterValuesFault")
        if (!faults.isArray || faults.isEmpty) return@runCatching null
        faults.joinToString("; ") { fault ->
            val parameter = fault.path("parameterName").asText("?")
            val faultString = fault.path("faultString").asText("Invalid parameter value")
            val faultCode = fault.path("faultCode").asText("")
            "$parameter: $faultString" + if (faultCode.isBlank()) "" else " ($faultCode)"
        }.take(500)
    }.getOrNull()
}

internal fun internetRetryArg(body: String?): String? {
    if (body.isNullOrBlank()) return null
    val row = runCatching { ObjectMapper().readTree(body).path("provisions") }.getOrNull()?.takeIf { it.isArray }?.get(0)
        ?: return null
    if (!row.isArray || row.size() < 2 || row[0].asText() != "gf-onboarding-v2-pppoe") return null
    return row[1].asText(null)?.takeIf { it.isNotBlank() }
}

internal fun faultDocumentId(body: String?): String? {
    if (body.isNullOrBlank()) return null
    return runCatching { ObjectMapper().readTree(body).path("_id").asText(null) }.getOrNull()?.takeIf { it.isNotBlank() }
}

private data class OnboardingV2TaskRow(
    val operationId: String,
    val action: String,
    val serial: String,
    val deviceId: String,
    val model: String,
    val firmware: String,
    val baselineCipher: String?,
    val desiredCipher: String?,
    val taskId: String?,
    val status: String,
)

/**
 * Keeps only task identity/evidence. Credentials are sent once to GenieACS in a
 * sensitive task payload and are deliberately never written to the ACS schema.
 */
class OnboardingV2TaskService(
    private val jdbc: JdbcTemplate,
    private val client: GenieAcsClient,
    private val json: ObjectMapper,
    private val baselineCipher: OnboardingV2BaselineCipher,
) {
    private val log = LoggerFactory.getLogger(OnboardingV2TaskService::class.java)

    fun enqueueInternet(request: OnboardingV2InternetRequest): OnboardingV2TaskResponse {
        validate(request)
        val row = claim(request)
        row.taskId?.let { return row.response() }
        confirmCurrentDevice(request)
        val args = json.writeValueAsString(mapOf(
            "operationId" to request.operationId,
            "expectedSerial" to request.sn.trim().uppercase(),
            "expectedModel" to request.model,
            "expectedFirmware" to request.firmware,
            "username" to request.username,
            "password" to request.password,
            "vlan" to request.vlan,
            "mode" to request.mode,
            "ip" to request.ip,
            "subnetMask" to request.subnetMask,
            "gateway" to request.gateway,
            "dns" to request.dns,
        ))
        log.info(
            "Onboarding v2 internet enqueue operationId={} deviceId={} model={} firmware={} mode={} vlan={} usernamePresent={} passwordPresent={}",
            request.operationId,
            row.deviceId,
            request.model,
            request.firmware,
            request.mode,
            request.vlan,
            request.username.isNotBlank(),
            request.password.isNotBlank(),
        )
        var queued = client.enqueueProvisions(row.deviceId, INTERNET_PROVISION, listOf(args), connectionRequest = true)
        if (queued.connectionRequestFailed) {
            queued = client.enqueueProvisions(row.deviceId, INTERNET_PROVISION, listOf(args), connectionRequest = false)
        }
        val taskId = queued.taskId?.takeIf { queued.accepted && it.isNotBlank() }
        if (taskId == null) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "GenieACS did not accept onboarding v2 internet task")
        }
        jdbc.update(
            """UPDATE acs_onboarding_v2_task SET genie_task_id=?, status='QUEUED', updated_at_epoch_ms=?
                WHERE operation_id=? AND action=?""",
            taskId, System.currentTimeMillis(), row.operationId, row.action,
        )
        return OnboardingV2TaskResponse(row.operationId, row.action, taskId, "QUEUED")
    }

    fun compensateInternet(request: com.dscorp.wispadmin.acs.OnboardingV2InternetCompensateRequest): OnboardingV2TaskResponse {
        val synthetic = OnboardingV2InternetRequest(request.operationId, request.sn, request.deviceId, request.model,
            request.firmware, "compensate", "compensate", MANAGEMENT_VLAN + 1)
        validate(synthetic)
        val row = claim(synthetic, INTERNET_COMPENSATE_ACTION)
        row.taskId?.let { return row.response() }
        confirmCurrentDevice(synthetic)
        val args = json.writeValueAsString(mapOf(
            "operationId" to request.operationId, "expectedSerial" to request.sn.trim().uppercase(),
            "expectedModel" to request.model, "expectedFirmware" to request.firmware,
            "mode" to "internet", "internetWasAbsent" to true,
        ))
        val queued = client.enqueueProvisions(row.deviceId, COMPENSATE_PROVISION, listOf(args), connectionRequest = false)
        val taskId = queued.taskId?.takeIf { queued.accepted && it.isNotBlank() }
            ?: throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "GenieACS did not accept onboarding v2 compensation task")
        jdbc.update("UPDATE acs_onboarding_v2_task SET genie_task_id=?, status='QUEUED', updated_at_epoch_ms=? WHERE operation_id=? AND action=?",
            taskId, System.currentTimeMillis(), row.operationId, row.action)
        return OnboardingV2TaskResponse(row.operationId, row.action, taskId, "QUEUED")
    }

    fun internetStatus(request: com.dscorp.wispadmin.acs.OnboardingV2InternetStatusRequest, compensation: Boolean): com.dscorp.wispadmin.acs.OnboardingV2InternetStatusResponse {
        val action = if (compensation) INTERNET_COMPENSATE_ACTION else INTERNET_ACTION
        val row = find(request.operationId, action)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Onboarding v2 task was not found")
        val synthetic = OnboardingV2InternetRequest(request.operationId, request.sn, request.deviceId, request.model,
            request.firmware, "status", "status", MANAGEMENT_VLAN + 1)
        validate(synthetic)
        if (row.serial != request.sn.trim().uppercase() || row.deviceId != request.deviceId || row.model != request.model || row.firmware != request.firmware) {
            conflict("Onboarding v2 task identity changed")
        }
        val taskId = row.taskId ?: return com.dscorp.wispadmin.acs.OnboardingV2InternetStatusResponse("WAITING", "")
        val fault = client.findFaultBodyForTask(row.deviceId, taskId)
        if (fault != null) {
            val reason = onboardingFaultReason(fault)
            log.warn(
                "Onboarding v2 GenieACS fault operationId={} taskId={} deviceId={} model={} firmware={} detail={}",
                row.operationId,
                taskId,
                row.deviceId,
                row.model,
                row.firmware,
                reason ?: "unknown",
            )
            val retry = !compensation &&
                sessionDropDecision(reason, row.status == SESSION_RETRY_STATUS) == SessionDropDecision.RETRY
            if (retry) {
                requeueAfterSessionDrop(row, fault)?.let { return it }
            }
            return com.dscorp.wispadmin.acs.OnboardingV2InternetStatusResponse("FAILED", taskId, reason)
        }
        val wcd = OnboardingV2CpeLayouts.of(row.model)?.wanConnectionDevice
            ?: conflict("Unsupported v2 model")
        val ownedName = onboardingOwnerMarker(row.operationId)
        val ppp = client.findWanPppConnections(row.deviceId, wcd, ownedName)
        val ip = client.findWanIpConnections(row.deviceId, wcd, ownedName)
        if (ppp.size + ip.size > 1) conflict("Multiple owned v2 WANs were found")
        val owned = refreshOwnedWan(row.deviceId, wcd, ownedName, ppp.singleOrNull() ?: ip.singleOrNull(), compensation)
        val state = if (compensation) {
            if (owned == null) "COMPLETE" else "WAITING"
        } else {
            if (owned?.connectionStatus.equals("Connected", ignoreCase = true)) "COMPLETE" else "WAITING"
        }
        return com.dscorp.wispadmin.acs.OnboardingV2InternetStatusResponse(state, taskId)
    }

    private fun requeueAfterSessionDrop(
        row: OnboardingV2TaskRow,
        fault: String,
    ): com.dscorp.wispadmin.acs.OnboardingV2InternetStatusResponse? {
        val arg = internetRetryArg(fault) ?: return null
        val faultId = faultDocumentId(fault) ?: return null
        if (!client.deleteFault(faultId)) return null
        val queued = client.enqueueProvisions(row.deviceId, INTERNET_PROVISION, listOf(arg), connectionRequest = false)
        val taskId = queued.taskId?.takeIf { queued.accepted && it.isNotBlank() } ?: return null
        jdbc.update(
            """UPDATE acs_onboarding_v2_task SET genie_task_id=?, status=?, updated_at_epoch_ms=?
                WHERE operation_id=? AND action=?""",
            taskId, SESSION_RETRY_STATUS, System.currentTimeMillis(), row.operationId, row.action,
        )
        return com.dscorp.wispadmin.acs.OnboardingV2InternetStatusResponse("WAITING", taskId)
    }

    fun enqueueWifi(request: OnboardingV2WifiRequest): OnboardingV2TaskResponse {
        val identity = identity(request.operationId, request.sn, request.deviceId, request.model, request.firmware)
        require(request.ssid24.isNotBlank() && request.ssid5.isNotBlank() && request.passphrase24.length in 8..63 && request.passphrase5.length in 8..63) { "WIFI_INPUT_REQUIRED" }
        var row = claim(identity, WIFI_ACTION)
        row.taskId?.let { return row.response() }
        confirmCurrentDevice(identity)
        if (row.baselineCipher == null) {
            val baseline = readWifiBaseline(identity)
            jdbc.update("UPDATE acs_onboarding_v2_task SET baseline_cipher=?, updated_at_epoch_ms=? WHERE operation_id=? AND action=?",
                baselineCipher.encrypt(json.writeValueAsString(baseline)), System.currentTimeMillis(), row.operationId, row.action)
            row = requireNotNull(find(row.operationId, row.action))
        }
        val desired = mapOf("ssid24" to request.ssid24, "passphrase24" to request.passphrase24, "enabled24" to true,
            "ssid5" to request.ssid5, "passphrase5" to request.passphrase5, "enabled5" to true)
        jdbc.update("UPDATE acs_onboarding_v2_task SET desired_cipher=?, updated_at_epoch_ms=? WHERE operation_id=? AND action=?",
            baselineCipher.encrypt(json.writeValueAsString(desired)), System.currentTimeMillis(), row.operationId, row.action)
        val args = json.writeValueAsString(mapOf("operationId" to request.operationId, "expectedSerial" to identity.sn.trim().uppercase(),
            "expectedModel" to identity.model, "expectedFirmware" to identity.firmware, "ssid24" to request.ssid24,
            "ssid5" to request.ssid5, "passphrase24" to request.passphrase24, "passphrase5" to request.passphrase5))
        val queued = client.enqueueProvisions(row.deviceId, WIFI_PROVISION, listOf(args), connectionRequest = true)
        val taskId = queued.taskId?.takeIf { queued.accepted && it.isNotBlank() }
            ?: throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "GenieACS did not accept onboarding v2 Wi-Fi task")
        jdbc.update("UPDATE acs_onboarding_v2_task SET genie_task_id=?, status='QUEUED', updated_at_epoch_ms=? WHERE operation_id=? AND action=?",
            taskId, System.currentTimeMillis(), row.operationId, row.action)
        return OnboardingV2TaskResponse(row.operationId, row.action, taskId, "QUEUED")
    }

    fun wifiStatus(request: OnboardingV2WifiCompensateRequest, compensation: Boolean): com.dscorp.wispadmin.acs.OnboardingV2InternetStatusResponse {
        val action = if (compensation) WIFI_COMPENSATE_ACTION else WIFI_ACTION
        val row = find(request.operationId, action) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Wi-Fi task was not found")
        val identity = identity(request.operationId, request.sn, request.deviceId, request.model, request.firmware)
        validate(identity)
        if (row.serial != request.sn.trim().uppercase() || row.deviceId != request.deviceId || row.model != request.model || row.firmware != request.firmware) conflict("Wi-Fi task identity changed")
        val taskId = row.taskId ?: return com.dscorp.wispadmin.acs.OnboardingV2InternetStatusResponse("WAITING", "")
        if (client.findFaultBodyForTask(row.deviceId, taskId) != null) return com.dscorp.wispadmin.acs.OnboardingV2InternetStatusResponse("FAILED", taskId)
        val source = find(request.operationId, WIFI_ACTION) ?: conflict("Wi-Fi baseline was not found")
        val cipher = (if (compensation) source.baselineCipher else source.desiredCipher)
            ?: conflict("Wi-Fi expected state was not captured")
        val expected = json.readTree(baselineCipher.decrypt(cipher))
        val actual = json.valueToTree<com.fasterxml.jackson.databind.JsonNode>(readWifiBaseline(identity))
        val complete = wifiEvidenceMatches(expected, actual)
        return com.dscorp.wispadmin.acs.OnboardingV2InternetStatusResponse(if (complete) "COMPLETE" else "WAITING", taskId)
    }

    fun compensateWifi(request: OnboardingV2WifiCompensateRequest): OnboardingV2TaskResponse {
        val identity = identity(request.operationId, request.sn, request.deviceId, request.model, request.firmware)
        val original = find(request.operationId, WIFI_ACTION) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Wi-Fi baseline was not found")
        val snapshot = original.baselineCipher?.let { baselineCipher.decrypt(it) }
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Wi-Fi baseline was not captured")
        val row = claim(identity, WIFI_COMPENSATE_ACTION)
        row.taskId?.let { return row.response() }
        confirmCurrentDevice(identity)
        val args = json.writeValueAsString(mapOf("operationId" to request.operationId, "expectedSerial" to identity.sn.trim().uppercase(),
            "expectedModel" to identity.model, "expectedFirmware" to identity.firmware, "mode" to "wifi", "previousWifi" to json.readTree(snapshot)))
        val queued = client.enqueueProvisions(row.deviceId, COMPENSATE_PROVISION, listOf(args), connectionRequest = false)
        val taskId = queued.taskId?.takeIf { queued.accepted && it.isNotBlank() }
            ?: throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "GenieACS did not accept onboarding v2 Wi-Fi compensation")
        jdbc.update("UPDATE acs_onboarding_v2_task SET genie_task_id=?, status='QUEUED', updated_at_epoch_ms=? WHERE operation_id=? AND action=?",
            taskId, System.currentTimeMillis(), row.operationId, row.action)
        return OnboardingV2TaskResponse(row.operationId, row.action, taskId, "QUEUED")
    }

    fun enqueueWanCleanup(request: OnboardingV2WanCleanupRequest): OnboardingV2TaskResponse {
        val identity = identity(request.operationId, request.sn, request.deviceId, request.model, request.firmware)
        require(request.mode == "pppoe" || request.mode == "static") { "INVALID_INTERNET_MODE" }
        val row = claim(identity, WAN_CLEANUP_ACTION)
        row.taskId?.let { return row.response() }
        confirmCurrentDevice(identity)
        val args = json.writeValueAsString(mapOf(
            "operationId" to request.operationId,
            "expectedSerial" to identity.sn.trim().uppercase(),
            "expectedModel" to identity.model,
            "expectedFirmware" to identity.firmware,
            "mode" to request.mode,
        ))
        val queued = client.enqueueProvisions(row.deviceId, WAN_CLEANUP_PROVISION, listOf(args), connectionRequest = false)
        val taskId = queued.taskId?.takeIf { queued.accepted && it.isNotBlank() }
            ?: throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "GenieACS did not accept onboarding v2 WAN cleanup task")
        jdbc.update("""UPDATE acs_onboarding_v2_task SET genie_task_id=?, status='QUEUED', updated_at_epoch_ms=?
            WHERE operation_id=? AND action=?""", taskId, System.currentTimeMillis(), row.operationId, row.action)
        return OnboardingV2TaskResponse(row.operationId, row.action, taskId, "QUEUED")
    }

    fun wanCleanupStatus(request: OnboardingV2WanCleanupRequest): com.dscorp.wispadmin.acs.OnboardingV2InternetStatusResponse {
        val row = find(request.operationId, WAN_CLEANUP_ACTION)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Onboarding v2 WAN cleanup task was not found")
        val identity = identity(request.operationId, request.sn, request.deviceId, request.model, request.firmware)
        validate(identity)
        if (row.serial != request.sn.trim().uppercase() || row.deviceId != request.deviceId || row.model != request.model || row.firmware != request.firmware) {
            conflict("Onboarding v2 WAN cleanup identity changed")
        }
        val taskId = row.taskId ?: return com.dscorp.wispadmin.acs.OnboardingV2InternetStatusResponse("WAITING", "")
        val fault = client.findFaultBodyForTask(row.deviceId, taskId)
        if (fault != null) {
            val reason = onboardingFaultReason(fault)
            log.warn("Onboarding v2 WAN cleanup fault operationId={} taskId={} deviceId={} detail={}", row.operationId, taskId, row.deviceId, reason ?: "unknown")
            return com.dscorp.wispadmin.acs.OnboardingV2InternetStatusResponse("FAILED", taskId, reason)
        }
        return com.dscorp.wispadmin.acs.OnboardingV2InternetStatusResponse(
            if (client.isTaskPending(taskId)) "WAITING" else "COMPLETE",
            taskId,
        )
    }

    private fun claim(request: OnboardingV2InternetRequest, action: String = INTERNET_ACTION): OnboardingV2TaskRow {
        val serial = request.sn.trim().uppercase()
        val existing = find(request.operationId, action)
        if (existing != null) {
            if (existing.serial != serial || existing.deviceId != request.deviceId || existing.model != request.model ||
                existing.firmware != request.firmware) conflict("Onboarding v2 task identity changed")
            return existing
        }
        val now = System.currentTimeMillis()
        try {
            jdbc.update(
                """INSERT INTO acs_onboarding_v2_task
                    (operation_id, action, sn, device_id, model, firmware, status, created_at_epoch_ms, updated_at_epoch_ms)
                    VALUES (?,?,?,?,?,?,'INTENT',?,?)""",
                request.operationId, action, serial, request.deviceId, request.model, request.firmware, now, now,
            )
        } catch (_: DuplicateKeyException) {
            return claim(request, action)
        }
        return find(request.operationId, action) ?: error("Onboarding v2 task intent was not persisted")
    }

    private fun identity(operationId: String, sn: String, deviceId: String, model: String, firmware: String) =
        OnboardingV2InternetRequest(operationId, sn, deviceId, model, firmware, "identity", "identity", MANAGEMENT_VLAN + 1)

    private fun refreshOwnedWan(
        deviceId: String,
        wanConnectionDevice: Int,
        name: String,
        owned: com.dscorp.wispadmin.acs.genieacs.GenieAcsWanPppConnection?,
        compensation: Boolean,
    ): com.dscorp.wispadmin.acs.genieacs.GenieAcsWanPppConnection? {
        if (owned == null || !wanStatusNeedsRefresh(compensation)) return owned
        client.getParameterValues(deviceId, listOf(
            "${owned.path}.ConnectionStatus",
            "${owned.path}.LastConnectionError",
            "${owned.path}.ExternalIPAddress",
        ), connectionRequest = true)
        val ppp = client.findWanPppConnections(deviceId, wanConnectionDevice, name)
        val ip = client.findWanIpConnections(deviceId, wanConnectionDevice, name)
        if (ppp.size + ip.size > 1) conflict("Multiple owned v2 WANs were found")
        return ppp.singleOrNull() ?: ip.singleOrNull()
    }

    private fun readWifiBaseline(request: OnboardingV2InternetRequest): Map<String, Any> {
        val layout = OnboardingV2CpeLayouts.of(request.model) ?: conflict("Unsupported v2 model")
        val bands = layout.wifi24Index to layout.wifi5Index
        val leaves = listOf("SSID", "Enable", "KeyPassphrase")
        client.getParameterValues(request.deviceId, listOf(bands.first, bands.second).flatMap { index ->
            leaves.map { leaf -> "InternetGatewayDevice.LANDevice.1.WLANConfiguration.$index.$leaf" }
        }, connectionRequest = true)
        fun read(index: Int, leaf: String, required: Boolean): String {
            val value = client.getDeviceParameterValue(request.deviceId,
                "InternetGatewayDevice.LANDevice.1.WLANConfiguration.$index.$leaf").orEmpty()
            if (required && value.isBlank()) throw ResponseStatusException(HttpStatus.CONFLICT, "Wi-Fi baseline is not readable")
            return value
        }
        return mapOf("ssid24" to read(bands.first, "SSID", true), "passphrase24" to read(bands.first, "KeyPassphrase", false),
            "enabled24" to read(bands.first, "Enable", true).equals("true", true), "ssid5" to read(bands.second, "SSID", true),
            "passphrase5" to read(bands.second, "KeyPassphrase", false), "enabled5" to read(bands.second, "Enable", true).equals("true", true))
    }

    private fun wifiEvidenceMatches(expected: com.fasterxml.jackson.databind.JsonNode, actual: com.fasterxml.jackson.databind.JsonNode): Boolean {
        val wanted = expected.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
        val seen = actual.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
        for (key in listOf("passphrase24", "passphrase5")) {
            if (seen.path(key).asText("").isBlank()) {
                wanted.remove(key)
                seen.remove(key)
            }
        }
        return wanted == seen
    }

    private fun confirmCurrentDevice(request: OnboardingV2InternetRequest) {
        val serial = request.sn.trim().uppercase()
        val suffix = Tr069SerialMatcher.normalizeSuffix(serial) ?: conflict("ACS device is no longer uniquely identified")
        val matches = client.findDeviceBySerialSuffix(suffix).filter { device -> device.id == request.deviceId }
        val device = matches.singleOrNull() ?: conflict("ACS device is no longer uniquely identified")
        if (device.productClass?.trim() != request.model || device.softwareVersion?.trim() != request.firmware) {
            conflict("ACS device model or firmware changed")
        }
    }

    private fun validate(request: OnboardingV2InternetRequest) {
        require(request.operationId.matches(Regex("[a-zA-Z0-9-]{8,64}"))) { "INVALID_OPERATION_ID" }
        require(request.sn.trim().uppercase().matches(Regex("[A-Z0-9]{12,16}"))) { "INVALID_ONU_SERIAL" }
        require(request.deviceId.isNotBlank() && OnboardingV2CpeLayouts.supported(request.model)) { "UNSUPPORTED_ONU_MODEL" }
        require(request.firmware.isNotBlank()) { "INTERNET_INPUT_REQUIRED" }
        if (request.mode == "static") require(!request.ip.isNullOrBlank()) { "INTERNET_INPUT_REQUIRED" }
        else require(request.mode == "pppoe" && request.username.isNotBlank() && request.password.isNotBlank()) { "INTERNET_INPUT_REQUIRED" }
        require(request.vlan in 1..4094 && request.vlan != MANAGEMENT_VLAN) { "INVALID_INTERNET_VLAN" }
    }

    private fun find(operationId: String, action: String): OnboardingV2TaskRow? = jdbc.query(
        """SELECT operation_id, action, sn, device_id, model, firmware, baseline_cipher, desired_cipher, genie_task_id, status
            FROM acs_onboarding_v2_task WHERE operation_id=? AND action=?""",
        { rs, _ -> OnboardingV2TaskRow(rs.getString("operation_id"), rs.getString("action"), rs.getString("sn"),
            rs.getString("device_id"), rs.getString("model"), rs.getString("firmware"), rs.getString("baseline_cipher"), rs.getString("desired_cipher"), rs.getString("genie_task_id"), rs.getString("status")) },
        operationId, action,
    ).singleOrNull()

    private fun OnboardingV2TaskRow.response() = OnboardingV2TaskResponse(operationId, action, taskId!!, status)
    private fun conflict(message: String): Nothing = throw ResponseStatusException(HttpStatus.CONFLICT, message)

    private companion object {
        const val INTERNET_ACTION = "INTERNET"
        const val INTERNET_COMPENSATE_ACTION = "INTERNET_COMPENSATE"
        const val INTERNET_PROVISION = "gf-onboarding-v2-pppoe"
        const val COMPENSATE_PROVISION = "gf-onboarding-v2-compensate"
        const val WIFI_ACTION = "WIFI"
        const val WIFI_COMPENSATE_ACTION = "WIFI_COMPENSATE"
        const val WIFI_PROVISION = "gf-onboarding-v2-wifi"
        const val WAN_CLEANUP_ACTION = "WAN_CLEANUP"
        const val WAN_CLEANUP_PROVISION = "gf-onboarding-v2-cleanup"
        const val MANAGEMENT_VLAN = 1000
    }
}
