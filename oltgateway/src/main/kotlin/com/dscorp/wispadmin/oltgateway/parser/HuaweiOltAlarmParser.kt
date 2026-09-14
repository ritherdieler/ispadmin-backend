package com.dscorp.wispadmin.oltgateway.parser

data class ParsedOltAlarm(
    val alarmSequence: Long?,
    val alarmIdHex: String?,
    val severityRaw: String?,
    val raisedAtRaw: String?,
    val alarmName: String,
    val frameId: Int?,
    val slotId: Int?,
    val portId: Int?,
    val ontId: Int?,
    val equipmentId: String?,
    val description: String?,
    val cause: String?,
    val advice: String?,
    val reasonCode: String,
    val severity: String,
    val component: String,
    val isClear: Boolean,
    val rawBlock: String
)

class HuaweiOltAlarmParser {

    fun parseActiveAlarms(raw: String): List<ParsedOltAlarm> {
        if (raw.isBlank()) return emptyList()
        val normalized = raw.replace("\r\n", "\n").trim()
        val parts = normalized.split(END_MARKER)
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (parts.isEmpty()) {
            return listOf(unparsedAlarm(normalized))
        }
        val hasEndMarker = END_MARKER.containsMatchIn(normalized)
        if (!hasEndMarker && parts.size == 1 && !parts[0].contains("ALARM NAME", ignoreCase = true)) {
            return listOf(unparsedAlarm(parts[0]))
        }
        return parts.map { part -> parseBlock(part) ?: unparsedAlarm(part) }
    }

    private fun unparsedAlarm(rawBlock: String): ParsedOltAlarm {
        return ParsedOltAlarm(
            alarmSequence = null,
            alarmIdHex = extractLooseAlarmId(rawBlock),
            severityRaw = null,
            raisedAtRaw = null,
            alarmName = "UNPARSED",
            frameId = null,
            slotId = null,
            portId = null,
            ontId = null,
            equipmentId = null,
            description = null,
            cause = null,
            advice = null,
            reasonCode = REASON_UNPARSED,
            severity = "P2",
            component = "olt",
            isClear = false,
            rawBlock = rawBlock
        )
    }

    private fun extractLooseAlarmId(rawBlock: String): String? {
        return Regex("""0x[0-9a-fA-F]+""").find(rawBlock)?.value?.lowercase()
    }

    private fun parseBlock(block: String): ParsedOltAlarm? {
        val alarmName = field(block, "ALARM NAME") ?: return null
        val parameters = field(block, "PARAMETERS").orEmpty()
        val description = field(block, "DESCRIPTION")
        val cause = field(block, "CAUSE")
        val advice = field(block, "ADVICE")
        val header = HEADER_REGEX.find(block)
        val alarmSequence = header?.groupValues?.get(1)?.toLongOrNull()
        val severityRaw = header?.groupValues?.get(2)
        val alarmIdHex = header?.groupValues?.get(3)?.lowercase()
        val raisedAtRaw = header?.groupValues?.get(4)
        val frameId = intParam(parameters, "FrameID")
        val slotId = intParam(parameters, "SlotID")
        val portId = intParam(parameters, "PortID")
        val ontId = intParam(parameters, "ONT ID")
        val equipmentId = stringParam(parameters, "Equipment ID")
        val reasonCode = mapReasonCode(alarmName, alarmIdHex)
        val severity = mapSeverity(reasonCode, severityRaw)
        val component = buildComponent(slotId, portId)
        val isClear = isClearAlarm(alarmName, alarmIdHex)
        return ParsedOltAlarm(
            alarmSequence = alarmSequence,
            alarmIdHex = alarmIdHex,
            severityRaw = severityRaw,
            raisedAtRaw = raisedAtRaw,
            alarmName = alarmName,
            frameId = frameId,
            slotId = slotId,
            portId = portId,
            ontId = ontId,
            equipmentId = equipmentId,
            description = description,
            cause = cause,
            advice = advice,
            reasonCode = reasonCode,
            severity = severity,
            component = component,
            isClear = isClear,
            rawBlock = block
        )
    }

    private fun field(block: String, name: String): String? {
        val regex = Regex(
            """(?im)^\s*${Regex.escape(name)}\s*:\s*(.*?)(?=^\s*[A-Z][A-Z0-9 _/-]+\s*:|\z)""",
            RegexOption.DOT_MATCHES_ALL
        )
        val match = regex.find(block) ?: return null
        return match.groupValues[1]
            .lines()
            .joinToString(" ") { it.trim() }
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifBlank { null }
    }

    private fun intParam(parameters: String, name: String): Int? {
        return Regex("""(?i)${Regex.escape(name)}\s*:\s*(-?\d+)""")
            .find(parameters)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
    }

    private fun stringParam(parameters: String, name: String): String? {
        return Regex("""(?i)${Regex.escape(name)}\s*:\s*([^,]+)""")
            .find(parameters)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.ifBlank { null }
    }

    private fun buildComponent(slotId: Int?, portId: Int?): String {
        if (slotId != null && portId != null) return "gpon-$slotId/$portId"
        if (slotId != null) return "board-$slotId"
        return "olt"
    }

    fun isClearAlarm(alarmName: String, alarmIdHex: String?): Boolean {
        val id = alarmIdHex?.lowercase()
        if (id != null && CLEAR_IDS.contains(id)) return true
        val name = alarmName.lowercase()
        if (name.contains("fails") || name.contains("failure") || name.contains("faulty") || name.contains("broken")) {
            return false
        }
        return name.contains("recovers") ||
            name.contains(" is successful") ||
            name.contains("reduces to the restore") ||
            Regex("""\brecover(?:ed|s)?\b""").containsMatchIn(name)
    }

    fun mapReasonCode(alarmName: String, alarmIdHex: String?): String {
        val id = alarmIdHex?.lowercase()
        FAULT_BY_ID[id]?.let { return it }
        CLEAR_BY_ID[id]?.let { return it }

        val name = alarmName.lowercase()
        return when {
            name.contains("rogue") -> "PON_ROGUE_ONT"
            name.contains("configuration recovery") -> "ONT_CONFIG_RECOVERY_FAIL"
            name.contains("dying-gasp") || name.contains("dying gasp") -> "ONT_DYING_GASP"
            name.contains("losi") || name.contains("lobi") ||
                (name.contains("distribute fiber") && name.contains("optical")) -> "ONT_OFFLINE"
            (name.contains("feeder") && containsGponPortLos(name)) ||
                (containsGponPortLos(name) &&
                    (name.contains("feeder") || name.contains("expected optical signals from onts"))) ->
                "PON_PORT_DOWN"
            name.contains("lofi") || name.contains("loss of frame of onti") -> "ONT_LOFI"
            name.contains("sfi") || name.contains("signal fail of onti") -> "ONT_SFI"
            name.contains("sdi") || name.contains("signal degrade of onti") -> "ONT_SDI"
            name.contains("lcdgi") || name.contains("gem channel delineation") -> "ONT_LCDGI"
            name.contains("rdii") || (name.contains("rdi") && name.contains("ont")) -> "ONT_RDI"
            name.contains("loami") || name.contains("lopci") || name.contains("loss of ploam") -> "ONT_LOAMI"
            name.contains("deactivation failure") || name.contains("(dfi)") -> "ONT_DFI"
            name.contains("physical equipment error") || name.contains("(peei)") -> "ONT_PEE"
            name.contains("initiative to go offline") -> "ONT_INITIATIVE_OFFLINE"
            name.contains("authentication") && name.contains("invalid") -> "ONT_AUTH_INVALID"
            name.contains("hardware of the gpon port") -> "PON_PORT_HW_FAULT"
            name.contains("optical transceiver of the pon port is absent") -> "PON_OPTICS_ABSENT"
            name.contains("failed in ranging") -> "PON_RANGING_FAIL"
            name.contains("numerous onts") && name.contains("powered off") -> "PON_MASS_POWER_OFF"
            name.contains("type b protection") || name.contains("backbone fiber on the port") ->
                "PON_PROTECTION_FIBER"
            name.contains("downstream signal degrade") -> "ONT_DOWNSTREAM_SD"
            name.contains("downstream signal fail") -> "ONT_DOWNSTREAM_SF"
            name.contains("optical transceiver parameters exceed alarm") -> "ONT_OPTICAL_ALARM"
            name.contains("optical transceiver parameters exceed warning") -> "ONT_OPTICAL_WARNING"
            name.contains("hardware of the ont") -> "ONT_HW_FAULT"
            name.contains("ethernet port of the ont") -> "ONT_ETH_LOS"
            name.contains("standby battery") || name.contains("battery of the ont") -> "ONT_BATTERY"
            name.contains("dowi") -> "ONT_DOWI_THRESHOLD"
            name.contains("fec") && name.contains("uncorrectable") -> "ONT_FEC_UNCORRECTABLE"
            name.contains("fec") && name.contains("correctable") -> "ONT_FEC_CORRECTABLE"
            name.contains("looci") -> "ONT_LOOCI_THRESHOLD"
            name.contains("control board") && (name.contains("fail") || name.contains("fault")) ->
                "OLT_CONTROL_BOARD_FAULT"
            name.contains("board") && (name.contains("fail") || name.contains("fault")) -> "OLT_BOARD_FAULT"
            name.contains("power") && (name.contains("fail") || name.contains("fault") || name.contains("abnormal")) ->
                "OLT_POWER_FAULT"
            name.contains("fan") && (name.contains("fail") || name.contains("fault") || name.contains("abnormal")) ->
                "OLT_FAN_FAULT"
            name.contains("temperature") && (name.contains("high") || name.contains("abnormal") || name.contains("exceed")) ->
                "OLT_TEMP_HIGH"
            name.contains("uplink") && (name.contains("down") || containsGponPortLos(name) || name.contains("fail")) ->
                "OLT_UPLINK_DOWN"
            containsGponPortLos(name) -> "PON_PORT_DOWN"
            else -> "OLT_ALARM"
        }
    }

    internal fun containsGponPortLos(name: String): Boolean {
        if (name.contains("losi") || name.contains("lobi")) return false
        if (name.contains("(los)")) return true
        return Regex("""\blos\b""").containsMatchIn(name)
    }

    private fun mapSeverity(reasonCode: String, severityRaw: String?): String {
        return when (reasonCode) {
            "PON_PORT_DOWN",
            "PON_ROGUE_ONT",
            "PON_PORT_HW_FAULT",
            "PON_OPTICS_ABSENT",
            "PON_MASS_POWER_OFF",
            "PON_PROTECTION_FIBER",
            "OLT_BOARD_FAULT",
            "OLT_CONTROL_BOARD_FAULT",
            "OLT_POWER_FAULT",
            "OLT_UPLINK_DOWN" -> "P0"

            "ONT_OFFLINE",
            "ONT_LOFI",
            "ONT_SFI",
            "ONT_LCDGI",
            "ONT_DOWNSTREAM_SF",
            "ONT_HW_FAULT",
            "ONT_AUTH_INVALID",
            "PON_RANGING_FAIL",
            "OLT_FAN_FAULT",
            "OLT_TEMP_HIGH" -> "P1"

            "ONT_DYING_GASP",
            "ONT_CONFIG_RECOVERY_FAIL",
            "ONT_SDI",
            "ONT_RDI",
            "ONT_LOAMI",
            "ONT_DFI",
            "ONT_PEE",
            "ONT_INITIATIVE_OFFLINE",
            "ONT_DOWNSTREAM_SD",
            "ONT_OPTICAL_ALARM",
            "ONT_OPTICAL_WARNING",
            "ONT_ETH_LOS",
            "ONT_BATTERY",
            "ONT_DOWI_THRESHOLD",
            "ONT_FEC_CORRECTABLE",
            "ONT_FEC_UNCORRECTABLE",
            "ONT_LOOCI_THRESHOLD" -> "P2"

            else -> when (severityRaw?.uppercase()) {
                "CRITICAL", "MAJOR" -> "P0"
                "WARNING", "MINOR" -> "P1"
                else -> "P2"
            }
        }
    }

    companion object {
        const val REASON_UNPARSED = "OLT_ALARM_UNPARSED"

        private val END_MARKER = Regex("""(?m)^\s*--- END\s*$""")
        private val HEADER_REGEX = Regex(
            """(?im)ALARM\s+(\d+)\s+FAULT\s+(\w+)\s+(0x[0-9a-fA-F]+)\s+.+?\s+(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}[^\n]*)"""
        )

        private val FAULT_BY_ID = mapOf(
            "0x2e11a001" to "PON_PORT_DOWN",
            "0x2e112007" to "ONT_OFFLINE",
            "0x2e11a00b" to "ONT_DYING_GASP",
            "0x2e112006" to "ONT_LOFI",
            "0x2e112004" to "ONT_SFI",
            "0x2e112003" to "ONT_SDI",
            "0x2e112002" to "ONT_LCDGI",
            "0x2e112001" to "ONT_RDI",
            "0x2e11a00c" to "ONT_LOAMI",
            "0x2e11a009" to "ONT_DFI",
            "0x2e11a00f" to "ONT_PEE",
            "0x2e111999" to "ONT_INITIATIVE_OFFLINE",
            "0x2e305015" to "ONT_AUTH_INVALID",
            "0x2e21a102" to "ONT_CONFIG_RECOVERY_FAIL",
            "0x2e314021" to "PON_ROGUE_ONT",
            "0x2e314022" to "PON_ROGUE_ONT",
            "0x2e11a002" to "PON_PORT_HW_FAULT",
            "0x2e314020" to "PON_OPTICS_ABSENT",
            "0x2e11999c" to "PON_RANGING_FAIL",
            "0x2e31305f" to "PON_MASS_POWER_OFF",
            "0x2e11a523" to "PON_PROTECTION_FIBER",
            "0x2e11999e" to "ONT_DOWNSTREAM_SD",
            "0x2e11999f" to "ONT_DOWNSTREAM_SF",
            "0x2e31305c" to "ONT_OPTICAL_ALARM",
            "0x2e31305e" to "ONT_OPTICAL_ALARM",
            "0x2e313060" to "ONT_OPTICAL_WARNING",
            "0x2e313062" to "ONT_OPTICAL_WARNING",
            "0x2e313015" to "ONT_HW_FAULT",
            "0x2e313024" to "ONT_ETH_LOS",
            "0x2e313016" to "ONT_BATTERY",
            "0x2e313017" to "ONT_BATTERY",
            "0x2e313018" to "ONT_BATTERY",
            "0x2e313019" to "ONT_BATTERY",
            "0x2e11a524" to "ONT_DOWI_THRESHOLD",
            "0x2e112009" to "ONT_FEC_CORRECTABLE",
            "0x2e11200a" to "ONT_FEC_UNCORRECTABLE",
            "0x2e11a104" to "ONT_LOOCI_THRESHOLD"
        )

        private val CLEAR_BY_ID = mapOf(
            "0x2e12a001" to "PON_PORT_DOWN",
            "0x2e122007" to "ONT_OFFLINE",
            "0x2e12a00b" to "ONT_DYING_GASP",
            "0x2e122006" to "ONT_LOFI",
            "0x2e122004" to "ONT_SFI",
            "0x2e122003" to "ONT_SDI",
            "0x2e122002" to "ONT_LCDGI",
            "0x2e122001" to "ONT_RDI",
            "0x2e12a00c" to "ONT_LOAMI",
            "0x2e12a009" to "ONT_DFI",
            "0x2e12a00f" to "ONT_PEE",
            "0x2e121999" to "ONT_INITIATIVE_OFFLINE",
            "0x2e22a102" to "ONT_CONFIG_RECOVERY_FAIL",
            "0x2e12a002" to "PON_PORT_HW_FAULT",
            "0x2e12999c" to "PON_RANGING_FAIL",
            "0x2e12a523" to "PON_PROTECTION_FIBER",
            "0x2e12999e" to "ONT_DOWNSTREAM_SD",
            "0x2e12999f" to "ONT_DOWNSTREAM_SF",
            "0x2e12a524" to "ONT_DOWI_THRESHOLD",
            "0x2e122009" to "ONT_FEC_CORRECTABLE",
            "0x2e12200a" to "ONT_FEC_UNCORRECTABLE",
            "0x2e12a104" to "ONT_LOOCI_THRESHOLD"
        )

        private val CLEAR_IDS = CLEAR_BY_ID.keys
    }
}
