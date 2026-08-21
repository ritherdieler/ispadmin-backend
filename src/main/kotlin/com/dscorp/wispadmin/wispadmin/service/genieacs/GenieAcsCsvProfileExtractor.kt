package com.dscorp.wispadmin.wispadmin.service.genieacs

data class Tr069ProfileDraft(
    val productClass: String,
    val manufacturer: String?,
    val deviceId: String?,
    val serialNumber: String?,
    val wanConnectionDeviceIndex: Int,
    val wanIpConnectionPath: String,
    val wanGponLinkConfigPath: String?,
    val vlanParameters: List<Tr069VlanParameterSpec>,
    val wlan24Path: String?,
    val wlan5Path: String?,
    val wifiSecurityPrep: List<Tr069WifiSecurityPrepSpec> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    fun toModelProfile(): Tr069ModelProfile = Tr069ModelProfile(
        productClass = productClass,
        wanConnectionDeviceIndex = wanConnectionDeviceIndex,
        wanIpConnectionPath = wanIpConnectionPath,
        wanGponLinkConfigPath = wanGponLinkConfigPath,
        vlanParameters = vlanParameters,
        wlan24Path = wlan24Path ?: "",
        wlan5Path = wlan5Path ?: "",
        wifiSecurityPrep = wifiSecurityPrep,
    )
}

enum class Tr069VlanValueKind {
    VLAN_ID,
    ENABLE_ONE,
    ENABLE_TRUE,
}

data class Tr069VlanParameterSpec(
    val path: String,
    val valueKind: Tr069VlanValueKind = Tr069VlanValueKind.VLAN_ID,
)

data class GenieAcsCsvRow(
    val parameter: String,
    val writable: Boolean,
    val value: String?,
)

object GenieAcsCsvProfileExtractor {

    private val WAN_IP_SUFFIX = Regex("""\.WANConnectionDevice\.(\d+)\.WANIPConnection\.1\.ExternalIPAddress$""")
    private val WLAN_SSID_SUFFIX = Regex("""\.WLANConfiguration\.(\d+)\.SSID$""")
    private val WLAN_KEY_SUFFIX = Regex("""\.WLANConfiguration\.(\d+)\.KeyPassphrase$""")

    fun extract(csvContent: String): Tr069ProfileDraft {
        val rows = parseCsv(csvContent)
        val byParameter = rows.associateBy { it.parameter }

        val productClass = requireParam(byParameter, "DeviceID.ProductClass")
        val manufacturer = byParameter["DeviceID.Manufacturer"]?.value
        val deviceId = byParameter["DeviceID.ID"]?.value
        val serialNumber = byParameter["DeviceID.SerialNumber"]?.value

        val wanMatch = rows
            .filter { it.writable && WAN_IP_SUFFIX.containsMatchIn(it.parameter) }
            .minByOrNull { WAN_IP_SUFFIX.find(it.parameter)!!.groupValues[1].toInt() }
            ?: error("No se encontró WANIPConnection.1.ExternalIPAddress escribible")

        val wanIndex = WAN_IP_SUFFIX.find(wanMatch.parameter)!!.groupValues[1].toInt()
        val wanBase = wanMatch.parameter.removeSuffix(".ExternalIPAddress")

        val vlanParameters = mutableListOf<Tr069VlanParameterSpec>()
        rows.filter { it.writable && it.parameter.startsWith("$wanBase.") }
            .map { it.parameter }
            .filter { param ->
                val leaf = param.substringAfterLast('.')
                when {
                    leaf.contains("MultiCast", ignoreCase = true) -> false
                    leaf == "X_CT-COM_VLANIDMark" || leaf == "X_ZTE-COM_VLANID" || leaf == "X_HW_VLAN" -> true
                    leaf == "X_ZTE-COM_VLANEnable" -> true
                    else -> false
                }
            }
            .sorted()
            .forEach { path ->
                val leaf = path.substringAfterLast('.')
                val kind = when (leaf) {
                    "X_ZTE-COM_VLANEnable" -> Tr069VlanValueKind.ENABLE_ONE
                    else -> Tr069VlanValueKind.VLAN_ID
                }
                vlanParameters += Tr069VlanParameterSpec(path = path, valueKind = kind)
            }

        val wanGponVlan = rows
            .filter { it.writable && it.parameter.contains("WANGponLinkConfig") && it.parameter.endsWith(".VLANIDMark") }
            .minByOrNull { it.parameter }
            ?.parameter
        if (wanGponVlan != null) {
            vlanParameters += Tr069VlanParameterSpec(path = wanGponVlan)
        }

        val wanGponBase = byParameter.keys
            .filter { it.contains("WANConnectionDevice.$wanIndex.") && it.endsWith("WANGponLinkConfig") }
            .singleOrNull()

        val wlanCandidates = byParameter.entries
            .filter { (param, row) ->
                row.writable && (WLAN_SSID_SUFFIX.containsMatchIn(param) || WLAN_KEY_SUFFIX.containsMatchIn(param))
            }
            .map { it.key }
            .mapNotNull { param ->
                val index = WLAN_SSID_SUFFIX.find(param)?.groupValues?.get(1)
                    ?: WLAN_KEY_SUFFIX.find(param)?.groupValues?.get(1)
                    ?: return@mapNotNull null
                val base = param.substringBeforeLast('.')
                val ssid = byParameter["$base.SSID"]?.value.orEmpty()
                base to ssid
            }
            .distinctBy { it.first }

        val (wlan24, wlan5) = resolveWlanBands(wlanCandidates, productClass, manufacturer)
        val warnings = mutableListOf<String>()
        if (vlanParameters.isEmpty()) {
            warnings += "No se detectaron parámetros VLAN WAN escribibles."
        }
        if (wlan24 == null) warnings += "No se detectó radio WiFi 2.4 GHz."
        if (wlan5 == null) warnings += "No se detectó radio WiFi 5 GHz."

        val keyPassphraseByPath = rows
            .filter { WLAN_KEY_SUFFIX.containsMatchIn(it.parameter) }
            .associate { it.parameter to it.value }
        val wifiSecurityPrep = Tr069WifiSecurityDefaults.detectFromCsvExport(
            wlanPaths = listOf(wlan24, wlan5),
            keyPassphraseByPath = keyPassphraseByPath,
        )

        return Tr069ProfileDraft(
            productClass = productClass,
            manufacturer = manufacturer,
            deviceId = deviceId,
            serialNumber = serialNumber,
            wanConnectionDeviceIndex = wanIndex,
            wanIpConnectionPath = wanBase,
            wanGponLinkConfigPath = wanGponBase,
            vlanParameters = vlanParameters.distinctBy { it.path },
            wlan24Path = wlan24,
            wlan5Path = wlan5,
            wifiSecurityPrep = wifiSecurityPrep,
            warnings = warnings,
        )
    }

    internal fun parseCsv(csvContent: String): List<GenieAcsCsvRow> =
        csvContent.lineSequence()
            .drop(1)
            .filter { it.isNotBlank() }
            .mapNotNull { line -> parseCsvLine(line) }
            .toList()

    private fun parseCsvLine(line: String): GenieAcsCsvRow? {
        val fields = splitCsvFields(line)
        if (fields.size < 4) return null
        val parameter = fields[0].trim()
        if (parameter.isBlank()) return null
        val writable = fields[3].trim().equals("true", ignoreCase = true)
        val value = fields.getOrNull(5)?.trim()?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() }
        return GenieAcsCsvRow(parameter = parameter, writable = writable, value = value)
    }

    internal fun splitCsvFields(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == ',' && !inQuotes -> {
                    fields += current.toString()
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        fields += current.toString()
        return fields
    }

    private fun resolveWlanBands(
        candidates: List<Pair<String, String>>,
        productClass: String,
        manufacturer: String?,
    ): Pair<String?, String?> {
        if (candidates.isEmpty()) return null to null

        val tagged = candidates.map { (base, ssid) ->
            Triple(base, ssid, classifySsidBand(ssid))
        }

        val explicit24 = tagged.firstOrNull { it.third == WlanBand.BAND_24 }?.first
        val explicit5 = tagged.firstOrNull { it.third == WlanBand.BAND_5 }?.first
        if (explicit24 != null && explicit5 != null) {
            return explicit24 to explicit5
        }

        val sorted = candidates.sortedBy { wlanIndex(it.first) }
        if (sorted.size >= 2 && usesInvertedWlanIndexMapping(productClass, manufacturer)) {
            val idx5 = candidates.firstOrNull { wlanIndex(it.first) == 5 }?.first
            val idx1 = candidates.firstOrNull { wlanIndex(it.first) == 1 }?.first
            if (idx5 != null && idx1 != null) {
                return idx5 to idx1
            }
            return sorted.last().first to sorted.first().first
        }
        return when (sorted.size) {
            1 -> sorted.first().first to null
            else -> sorted.first().first to sorted.last().first
        }
    }

    private fun wlanIndex(basePath: String): Int =
        WLAN_SSID_SUFFIX.find("$basePath.SSID")?.groupValues?.get(1)?.toIntOrNull() ?: 0

    private fun usesInvertedWlanIndexMapping(productClass: String, manufacturer: String?): Boolean {
        val pc = productClass.uppercase()
        val vendor = manufacturer.orEmpty().uppercase()
        return pc.contains("V2804") || pc.contains("VSOL") || vendor.contains("REALTEK") || vendor.contains("VSOL")
    }

    private enum class WlanBand { BAND_24, BAND_5, UNKNOWN }

    private fun classifySsidBand(ssid: String): WlanBand {
        val normalized = ssid.uppercase()
        if (normalized.contains("5G")) return WlanBand.BAND_5
        if (normalized.contains("2.4") || normalized.contains("2.4G") ||
            (normalized.contains("2G") && !normalized.contains("5G"))
        ) {
            return WlanBand.BAND_24
        }
        return WlanBand.UNKNOWN
    }

    private fun requireParam(rows: Map<String, GenieAcsCsvRow>, key: String): String =
        rows[key]?.value?.trim()?.takeIf { it.isNotEmpty() }
            ?: error("Falta parámetro obligatorio en export CSV: $key")
}
