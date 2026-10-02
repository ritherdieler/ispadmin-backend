package com.dscorp.wispadmin.acs.service

import com.fasterxml.jackson.databind.JsonNode
import java.net.URI
import java.time.Instant
import java.security.MessageDigest

data class CpeWanSnapshot(
    val path: String,
    val ipAddress: String,
    val connectionStatus: String?,
    val observedAt: String?,
)

data class CpeInspectionSummary(
    val deviceId: String,
    val productClass: String?,
    val softwareVersion: String?,
    val lastInformAt: String?,
    val managementWan: CpeWanSnapshot?,
    val internetWan: CpeWanSnapshot?,
    val ssid24: String?,
    val ssid5: String?,
    val ssid24ObservedAt: String?,
    val ssid5ObservedAt: String?,
)

data class CpeTreeEntry(
    val name: String,
    val path: String,
    val objectNode: Boolean,
    val hasChildren: Boolean,
    val type: String?,
    val writable: Boolean?,
    val hasValue: Boolean,
    val value: Any?,
    val observedAt: String?,
    val redacted: Boolean,
)

data class CpeTreePage(
    val deviceId: String,
    val lastInformAt: String?,
    val parent: String?,
    val query: String?,
    val entries: List<CpeTreeEntry>,
    val truncated: Boolean,
)

data class CpeFaultView(
    val id: String,
    val deviceId: String,
    val channel: String?,
    val taskId: String?,
    val occurredAt: String?,
    val retries: Int?,
    val code: String,
    val description: String,
    val parameters: List<String>,
)

class CpeInspectionMapper {
    private val pathPattern = Regex("[A-Za-z0-9_.:-]{1,512}")
    private val codePattern = Regex("[A-Za-z0-9_.-]{1,64}")
    private val identifierPattern = Regex("[A-Za-z0-9_.:-]{1,256}")

    fun summary(device: JsonNode): CpeInspectionSummary {
        val model = device.path("_deviceId").path("_ProductClass").textOrNull()
        val wifiIndices = when (model?.uppercase()) {
            "F6600R" -> 1 to 5
            "V2804AX15T", "VSOLVA74" -> 5 to 1
            else -> null
        }
        val root = device.path("InternetGatewayDevice")
        val requestUrl = value(root.path("ManagementServer").path("ConnectionRequestURL"))?.toString()
        val managementIp = runCatching { URI.create(requestUrl).host }.getOrNull()
        val wan = wanConnections(root)
        fun serviceLabel(connection: CpeWanSnapshot): String {
            val node = connection.path.split('.').drop(1).fold(root) { current, segment -> current.path(segment) }
            return listOf("ServiceList", "Name", "ConnectionType")
                .mapNotNull { value(node.path(it))?.toString() }.joinToString(" ").uppercase()
        }
        val management = managementIp?.let { host -> wan.firstOrNull { it.ipAddress == host } }
            ?: wan.firstOrNull { serviceLabel(it).contains("TR069") }
        val internet = wan.asSequence()
            .filter { it.path != management?.path }
            .sortedWith(compareByDescending<CpeWanSnapshot> { serviceLabel(it).contains("INTERNET") }
                .thenByDescending { it.connectionStatus.equals("Connected", true) }
                .thenByDescending { it.path.contains("WANPPPConnection") })
            .firstOrNull()
        val wlan = root.path("LANDevice").path("1").path("WLANConfiguration")
        val ssid24Node = wifiIndices?.first?.let { wlan.path(it.toString()).path("SSID") }
        val ssid5Node = wifiIndices?.second?.let { wlan.path(it.toString()).path("SSID") }
        return CpeInspectionSummary(
            deviceId = device.path("_id").asText(),
            productClass = model,
            softwareVersion = value(root.path("DeviceInfo").path("SoftwareVersion"))?.toString(),
            lastInformAt = device.path("_lastInform").textOrNull(),
            managementWan = management,
            internetWan = internet,
            ssid24 = ssid24Node?.let(::value)?.toString(),
            ssid5 = ssid5Node?.let(::value)?.toString(),
            ssid24ObservedAt = ssid24Node?.path("_timestamp")?.textOrNull(),
            ssid5ObservedAt = ssid5Node?.path("_timestamp")?.textOrNull(),
        )
    }

    fun tree(device: JsonNode, parent: String?, query: String?): CpeTreePage {
        val normalizedParent = parent?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedQuery = query?.trim()?.takeIf { it.isNotEmpty() }
        require(normalizedParent == null || pathPattern.matches(normalizedParent)) { "Invalid parent path" }
        require(normalizedQuery == null || normalizedQuery.length <= 100) { "Invalid search query" }
        val entries = if (normalizedQuery == null) {
            val branch = normalizedParent?.split('.')?.fold(device) { node, segment -> node.path(segment) } ?: device
            require(!branch.isMissingNode) { "Unknown parent path" }
            children(branch, normalizedParent)
        } else {
            val matches = mutableListOf<CpeTreeEntry>()
            fun search(node: JsonNode, path: String?) {
                if (matches.size > 100) return
                for (entry in children(node, path)) {
                    if (entry.path.contains(normalizedQuery, ignoreCase = true)) matches += entry
                    if (entry.hasChildren) search(node.path(entry.name), entry.path)
                    if (matches.size > 100) return
                }
            }
            search(device, null)
            matches
        }
        return CpeTreePage(
            deviceId = device.path("_id").asText(),
            lastInformAt = device.path("_lastInform").textOrNull(),
            parent = normalizedParent,
            query = normalizedQuery,
            entries = entries.take(100),
            truncated = entries.size > 100,
        )
    }

    fun fault(raw: JsonNode): CpeFaultView {
        val fault = raw.path("fault").takeUnless { it.isMissingNode } ?: raw
        val code = fault.path("code").textOrNull()?.takeIf(codePattern::matches) ?: "unknown"
        val channel = raw.path("channel").textOrNull()?.takeIf(identifierPattern::matches)
        val rawId = raw.path("_id").asText().takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Fault ID missing")
        val id = if (identifierPattern.matches(rawId)) rawId else
            "hash-" + MessageDigest.getInstance("SHA-256").digest(rawId.toByteArray())
                .joinToString("") { "%02x".format(it) }
        val parameters = fault.path("detail").path("setParameterValuesFault")
            .takeIf { it.isArray }?.mapNotNull { row ->
                row.path("parameterName").textOrNull()?.takeIf(pathPattern::matches)
            }?.distinct()?.take(20).orEmpty()
        return CpeFaultView(
            id = id,
            deviceId = raw.path("device").asText(),
            channel = channel,
            taskId = channel?.takeIf { it.startsWith("task_") }?.removePrefix("task_"),
            occurredAt = raw.path("timestamp").textOrNull()?.let { runCatching { Instant.parse(it).toString() }.getOrNull() },
            retries = raw.path("retries").takeIf { it.isIntegralNumber }?.asInt(),
            code = code,
            description = when (code.lowercase()) {
                "cwmp.9002" -> "Error interno del equipo"
                "cwmp.9003" -> "Parámetros rechazados por el equipo"
                "cwmp.9005" -> "Parámetro desconocido"
                "cwmp.9007" -> "Valor de parámetro inválido"
                "script.too_many_commits", "too_many_commits" -> "Demasiadas operaciones en la sesión"
                else -> "Fallo reportado por el ACS"
            },
            parameters = parameters,
        )
    }

    private fun wanConnections(root: JsonNode): List<CpeWanSnapshot> {
        val result = mutableListOf<CpeWanSnapshot>()
        val wanDevices = root.path("WANDevice")
        for (device in wanDevices.fields().asSequence()) {
            if (device.key.toIntOrNull() == null) continue
            val connections = device.value.path("WANConnectionDevice")
            for (connection in connections.fields().asSequence()) {
                if (connection.key.toIntOrNull() == null) continue
                for (kind in listOf("WANIPConnection", "WANPPPConnection")) {
                    for (instance in connection.value.path(kind).fields().asSequence()) {
                        if (instance.key.toIntOrNull() == null) continue
                        val ipNode = instance.value.path("ExternalIPAddress")
                        val ip = value(ipNode)?.toString()?.takeIf { it.isNotBlank() && it != "0.0.0.0" } ?: continue
                        val path = "InternetGatewayDevice.WANDevice.${device.key}.WANConnectionDevice.${connection.key}.$kind.${instance.key}"
                        result += CpeWanSnapshot(path, ip, value(instance.value.path("ConnectionStatus"))?.toString(),
                            ipNode.path("_timestamp").textOrNull())
                    }
                }
            }
        }
        return result
    }

    private fun children(node: JsonNode, parent: String?): List<CpeTreeEntry> = buildList {
        node.fields().forEachRemaining { field ->
            if (field.key.startsWith("_")) return@forEachRemaining
            val path = if (parent == null) field.key else "$parent.${field.key}"
            val child = field.value
            val secret = isSecret(path)
            val raw = child.path("_value")
            add(CpeTreeEntry(
                name = field.key,
                path = path,
                objectNode = child.path("_object").asBoolean(false),
                hasChildren = child.fieldNames().asSequence().any { !it.startsWith("_") },
                type = child.path("_type").textOrNull(),
                writable = child.path("_writable").takeIf { it.isBoolean }?.asBoolean(),
                hasValue = !raw.isMissingNode,
                value = if (secret) null else value(child),
                observedAt = child.path("_timestamp").textOrNull(),
                redacted = secret,
            ))
        }
    }.sortedWith(compareBy<CpeTreeEntry> { it.name.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.name })

    private fun isSecret(path: String): Boolean {
        val leaf = path.substringAfterLast('.').lowercase()
        return listOf("password", "passphrase", "secret", "token", "privatekey", "presharedkey")
            .any(leaf::contains) || leaf.endsWith("key") ||
            (path.contains(".ManagementServer.") && leaf.contains("username"))
    }

    private fun value(node: JsonNode): Any? {
        val raw = node.path("_value")
        return when {
            raw.isMissingNode || raw.isNull -> null
            raw.isTextual -> raw.asText()
            raw.isBoolean -> raw.asBoolean()
            raw.isIntegralNumber -> raw.asLong()
            raw.isFloatingPointNumber -> raw.asDouble()
            else -> null
        }
    }

    private fun JsonNode.textOrNull(): String? =
        takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() }
}
