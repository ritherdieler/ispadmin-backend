package com.dscorp.wispadmin.events

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import java.time.Instant

object WifiNbiTelemetry {
    const val ROOT = "InternetGatewayDevice.LANDevice.1"
    const val MAX_STATIONS = 32
    const val MAX_HOSTS = 64
    const val INFORM_PARAM_MAX_LAG_SECONDS = 300L

    val stationFields = listOf(
        "AssociatedDeviceMACAddress", "AssociatedDeviceRssi", "AssociatedDeviceRate",
        "AssociatedDeviceName", "X_ZTE-COM_AssociatedDeviceName",
        "X_ZTE-COM_WLAN_SNR", "X_ZTE-COM_WLAN_Noise", "X_ZTE-COM_WLAN_PacketSend", "X_ZTE-COM_WLAN_PacketReceived",
        "X_HW_RSSI", "X_HW_SNR", "X_HW_Noise", "X_HW_RxRate", "X_HW_TxRate",
    )

    fun radios(model: String): Map<Int, String> = when (model.uppercase()) {
        "F6600R" -> mapOf(1 to "2.4", 5 to "5")
        "V2804AX15T" -> mapOf(1 to "5", 5 to "2.4")
        else -> emptyMap()
    }

    /**
     * Fallback read for when the Inform carried no payload. One NBI request
     * instead of the 43 that enumerating every leaf used to cost.
     *
     * Projects the `AssociatedDevice` and `Hosts.Host` subtrees, never the
     * `WLANConfiguration.N` node itself: that one also returns SSID and
     * KeyPassphrase in cleartext.
     */
    fun projection(): String = (
        listOf("_id", "_lastInform", "_lastBoot", "_deviceId",
            "InternetGatewayDevice.DeviceInfo.SoftwareVersion",
            "$ROOT.Hosts.HostNumberOfEntries", "$ROOT.Hosts.Host") +
            listOf(1, 5).flatMap { radio ->
                val p = "$ROOT.WLANConfiguration.$radio"
                listOf("$p.TotalAssociations", "$p.AssociatedDevice")
            }
        ).joinToString(",")

    fun hostLeaves(hostCount: Int): List<String> {
        val n = hostCount.coerceIn(0, MAX_HOSTS)
        return (1..n).flatMap { index ->
            listOf("$ROOT.Hosts.Host.$index.MACAddress", "$ROOT.Hosts.Host.$index.HostName")
        }
    }

    const val PAYLOAD_VERSION = 1
    const val MAX_LEAVES = 1200
    private val SENSITIVE = Regex("SSID|KeyPassphrase|PreSharedKey|Password|WEPKey", RegexOption.IGNORE_CASE)
    private val SEGMENT = Regex("^[A-Za-z0-9_-]+$")

    /**
     * Rebuild the NBI-shaped tree from the flat leaves the Inform provision
     * carried, so [parsePayload] stays the single place that decides what is
     * valid, fresh and complete.
     *
     * The provision declared every leaf against the session clock, so the
     * values belong to this CWMP session by construction and all share `at` as
     * their timestamp. That is a stronger guarantee than inspecting the NBI
     * cache, which only ever holds the previous session.
     *
     * Returns null when the payload is unusable, which sends the caller to the
     * NBI fallback rather than persisting something half-parsed.
     */
    fun expandInformLeaves(payload: JsonNode): JsonNode? {
        if (payload.path("v").asInt(-1) != PAYLOAD_VERSION) return null
        if (payload.path("root").asText() != ROOT) return null
        val at = payload.path("at").takeIf { it.isNumber }?.asLong()?.takeIf { it > 0 } ?: return null
        val leaves = payload.path("leaves").takeIf { it.isObject } ?: return null
        if (leaves.size() > MAX_LEAVES) return null

        val factory = JsonNodeFactory.instance
        val root = factory.objectNode()
        root.put("_lastInform", at)
        payload.path("deviceId").takeIf { it.isTextual }?.let { root.put("_id", it.asText()) }

        var kept = 0
        for ((key, value) in leaves.fields().asSequence().toList()) {
            if (SENSITIVE.containsMatchIn(key)) continue
            val segments = key.split('.')
            if (segments.isEmpty() || segments.any { !SEGMENT.matches(it) }) continue
            var cursor = root
            for (segment in (ROOT.split('.') + segments.dropLast(1))) {
                val child = cursor.get(segment)
                cursor = if (child is ObjectNode) child else cursor.putObject(segment)
            }
            val leaf = cursor.putObject(segments.last())
            leaf.set<JsonNode>("_value", value)
            leaf.put("_timestamp", at)
            kept += 1
        }
        return if (kept == 0 && leaves.size() > 0) null else root
    }

    fun node(root: JsonNode, path: String): JsonNode {
        var current = root
        for (part in path.split('.')) current = current.path(part)
        return current
    }

    fun parseInstant(n: JsonNode): Instant? = try {
        if (n.isNumber) Instant.ofEpochMilli(n.asLong()) else Instant.parse(n.asText())
    } catch (_: Exception) {
        null
    }

    fun timestamp(n: JsonNode): Instant? = parseInstant(n.path("_timestamp"))

    fun value(root: JsonNode, path: String): String? =
        node(root, path).path("_value").takeUnless { it.isMissingNode || it.isNull }?.asText()

    fun normalizeMac(address: String): String? {
        val normalized = address.replace(Regex("[^a-fA-F0-9]"), "").uppercase()
        return normalized.takeIf { it.length == 12 }
    }

    fun sanitizeDisplayName(raw: String?): String? {
        val cleaned = raw?.trim()?.replace(Regex("[\\p{Cntrl}]"), "")?.take(64)?.trim().orEmpty()
        if (cleaned.isEmpty()) return null
        if (normalizeMac(cleaned) != null) return null
        return cleaned
    }

    fun hostDisplayNames(root: JsonNode): Map<String, String> {
        val names = mutableMapOf<String, String>()
        for (index in 1..MAX_HOSTS) {
            val base = "$ROOT.Hosts.Host.$index"
            val mac = normalizeMac(value(root, "$base.MACAddress") ?: continue) ?: continue
            val name = sanitizeDisplayName(value(root, "$base.HostName")) ?: continue
            names.putIfAbsent(mac, name)
        }
        return names
    }

    fun qualityAt(observed: Instant?, now: Instant, freshSeconds: Long): String = when {
        observed == null -> "MISSING"
        observed.isAfter(now.plusSeconds(60)) -> "INVALID"
        observed.isBefore(now.minusSeconds(freshSeconds)) -> "STALE"
        else -> "FRESH"
    }

    fun snFromDeviceId(deviceId: String): String? {
        val parts = deviceId.split('-')
        return parts.getOrNull(2)?.takeIf { it.isNotBlank() }
    }

    fun parsePayload(root: JsonNode, model: String, sn: String, now: Instant): CpeInformPayload? {
        val inform = parseInstant(root.path("_lastInform")) ?: return null
        val deviceId = root.path("_id").asText("").ifBlank { sn }
        val radioMap = radios(model)
        if (radioMap.isEmpty()) {
            return CpeInformPayload(
                sn = sn,
                deviceId = deviceId,
                informAt = inform,
                model = model,
                qualityStatus = "UNSUPPORTED",
                complete = true,
            )
        }
        val hostEntries = node(root, "$ROOT.Hosts.HostNumberOfEntries").path("_value").asText().toIntOrNull()
        val lanDeviceCount = hostEntries?.takeIf { it >= 0 }?.coerceAtMost(MAX_HOSTS)
        val hostNames = hostDisplayNames(root)
        val times = mutableListOf<Instant>()
        val stations = mutableListOf<CpeInformStation>()
        var total = 0
        var complete = true
        var associated2g: Int? = null
        var associated5g: Int? = null
        var errorReason: String? = null
        for ((radio, band) in radioMap) {
            val p = "$ROOT.WLANConfiguration.$radio"
            val n = node(root, "$p.TotalAssociations")
            val observed = timestamp(n)
            val number = n.path("_value").asText().toIntOrNull()
            if (observed == null || observed.isBefore(inform.minusSeconds(INFORM_PARAM_MAX_LAG_SECONDS)) ||
                observed.isAfter(now.plusSeconds(60)) || number == null || number < 0
            ) {
                complete = false
                continue
            }
            times += observed
            total += number
            if (band == "2.4") associated2g = number else associated5g = number
            if (number == 0) continue
            if (number > MAX_STATIONS) errorReason = "STATION_LIMIT_EXCEEDED"
            for (index in 1..MAX_STATIONS) {
                val base = "$p.AssociatedDevice.$index"
                val addr = node(root, "$base.AssociatedDeviceMACAddress")
                val address = addr.path("_value").asText("")
                val addrAt = timestamp(addr)
                if (address.isBlank() || addrAt == null || addrAt.isBefore(inform.minusSeconds(INFORM_PARAM_MAX_LAG_SECONDS))) continue
                val metricTimes = mutableListOf<Instant>()
                fun metric(vararg fields: String): Double? {
                    for (field in fields) {
                        val v = node(root, "$base.$field")
                        val t = timestamp(v) ?: continue
                        if (t.isBefore(inform.minusSeconds(INFORM_PARAM_MAX_LAG_SECONDS)) || t.isAfter(now.plusSeconds(60))) continue
                        val numberValue = v.path("_value").asText().toDoubleOrNull()?.takeIf { it.isFinite() } ?: continue
                        metricTimes += t
                        return numberValue
                    }
                    return null
                }
                val rssi = metric("AssociatedDeviceRssi", "X_HW_RSSI")?.takeIf { it in -120.0..0.0 }
                val snr = metric("X_ZTE-COM_WLAN_SNR", "X_HW_SNR")?.takeIf { it in 0.0..100.0 }
                val noise = metric("X_ZTE-COM_WLAN_Noise", "X_HW_Noise")
                val rx = metric("X_HW_RxRate", "AssociatedDeviceRate")
                val tx = metric("X_HW_TxRate", "AssociatedDeviceRate")
                val ptx = metric("X_ZTE-COM_WLAN_PacketSend")?.toLong()
                val prx = metric("X_ZTE-COM_WLAN_PacketReceived")?.toLong()
                if (rssi == null && snr == null) continue
                val mac = normalizeMac(address) ?: continue
                val vendorName = sanitizeDisplayName(value(root, "$base.AssociatedDeviceName"))
                    ?: sanitizeDisplayName(value(root, "$base.X_ZTE-COM_AssociatedDeviceName"))
                val display = hostNames[mac] ?: vendorName
                val t = (metricTimes + addrAt).minOrNull() ?: continue
                stations += CpeInformStation(
                    macNormalized = mac,
                    band = band,
                    observedAt = t,
                    displayName = display,
                    rssi = rssi,
                    snr = snr,
                    noise = noise,
                    rxRate = rx,
                    txRate = tx,
                    packetsTx = ptx,
                    packetsRx = prx,
                )
            }
        }
        val observedAt = times.minOrNull()
        if (!complete) errorReason = errorReason ?: "PARAMETERS_NOT_REFRESHED_FOR_INFORM"
        return CpeInformPayload(
            sn = sn,
            deviceId = deviceId,
            informAt = inform,
            model = model,
            observedAt = observedAt,
            associated2g = associated2g,
            associated5g = associated5g,
            associatedDeviceCount = if (complete) total else null,
            lanDeviceCount = lanDeviceCount,
            qualityStatus = if (complete) qualityAt(observedAt, now, 7200) else "MISSING",
            complete = complete,
            errorReason = errorReason,
            stations = stations,
        )
    }
}
