package com.dscorp.wispadmin.oltgateway.parser

import org.springframework.stereotype.Component

@Component
class AutofindParser {

    fun parse(output: String): List<ParsedAutofindOnt> {
        val blocks = output.split(Regex("""(?=^\s*F/S/P\s*:)""", RegexOption.MULTILINE))
            .filter { it.contains(Regex("""F/S/P\s*:""", RegexOption.IGNORE_CASE)) }

        return blocks.mapNotNull { block ->
            val fsp = fieldValue(block, "F/S/P") ?: return@mapNotNull null
            val parts = fsp.split("/").mapNotNull { it.trim().toIntOrNull() }
            if (parts.size < 3) return@mapNotNull null
            val sn = fieldValue(block, "Ont SN") ?: return@mapNotNull null
            ParsedAutofindOnt(
                sn = sn,
                frame = parts[0],
                slot = parts[1],
                port = parts[2],
                vendorId = fieldValue(block, "VendorID"),
                equipmentId = fieldValue(block, "Ont EquipmentID"),
                softwareVersion = fieldValue(block, "Ont SoftwareVersion"),
                autofindTime = fieldValue(block, "Ont autofind time")
            )
        }
    }

    private fun fieldValue(block: String, key: String): String? {
        val regex = Regex("""(?i)^\s*$key\s*:\s*(.*?)\s*$""", RegexOption.MULTILINE)
        return regex.find(block)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
