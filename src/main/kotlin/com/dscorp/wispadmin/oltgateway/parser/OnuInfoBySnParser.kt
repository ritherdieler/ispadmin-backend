package com.dscorp.wispadmin.oltgateway.parser

import org.springframework.stereotype.Component

@Component
class OnuInfoBySnParser {

    private val fspRowRegex = Regex("""(?m)^\s*(\d+)/(\d+)/(\d+)\s+(\d+)\s+(.*)$""")
    private val fspFieldRegex = Regex("""(?i)^\s*F/S/P\s*:\s*(\d+)/(\d+)/(\d+)\s*$""", RegexOption.MULTILINE)
    private val ontIdFieldRegex = Regex("""(?i)^\s*ONT-ID\s*:\s*(\d+)\s*$""", RegexOption.MULTILINE)
    private val snVendorRegex = Regex("""\(([^)]+)\)""")

    fun parse(output: String): ParsedOnuBySn? {
        if (output.contains(Regex("""(?i)does not exist|Failure:"""))) {
            return null
        }
        val location = parseLocation(output) ?: return null
        val sn = parseSn(output) ?: return null
        return ParsedOnuBySn(
            sn = sn,
            frame = location.frame,
            slot = location.slot,
            port = location.port,
            ontId = location.ontId,
            description = fieldValue(output, "Description") ?: location.description,
            runState = fieldValue(output, "Run state"),
            controlFlag = fieldValue(output, "Control flag"),
            lineProfileId = fieldValue(output, "Line profile ID")?.toIntOrNull(),
            lineProfileName = fieldValue(output, "Line profile name"),
            serviceProfileId = fieldValue(output, "Service profile ID")?.toIntOrNull(),
            serviceProfileName = fieldValue(output, "Service profile name")
        )
    }

    private fun parseLocation(output: String): Location? {
        val fieldMatch = fspFieldRegex.find(output)
        if (fieldMatch != null) {
            val ontId = ontIdFieldRegex.find(output)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            return Location(
                frame = fieldMatch.groupValues[1].toInt(),
                slot = fieldMatch.groupValues[2].toInt(),
                port = fieldMatch.groupValues[3].toInt(),
                ontId = ontId,
                description = null
            )
        }
        val rowMatch = fspRowRegex.find(output) ?: return null
        return Location(
            frame = rowMatch.groupValues[1].toInt(),
            slot = rowMatch.groupValues[2].toInt(),
            port = rowMatch.groupValues[3].toInt(),
            ontId = rowMatch.groupValues[4].toInt(),
            description = rowMatch.groupValues[5].trim().takeIf { it.isNotEmpty() }
        )
    }

    private fun parseSn(output: String): String? {
        val raw = fieldValue(output, "SN") ?: return null
        val vendor = snVendorRegex.find(raw)?.groupValues?.get(1)?.replace("-", "")?.trim()
        if (!vendor.isNullOrEmpty()) {
            return vendor
        }
        return raw.substringBefore('(').trim().takeIf { it.isNotEmpty() }
    }

    private fun fieldValue(output: String, key: String): String? {
        val regex = Regex("""(?i)^\s*$key\s*:\s*(.*?)\s*$""", RegexOption.MULTILINE)
        return regex.find(output)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private data class Location(
        val frame: Int,
        val slot: Int,
        val port: Int,
        val ontId: Int,
        val description: String?
    )
}
