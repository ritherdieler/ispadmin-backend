package com.dscorp.wispadmin.oltgateway.parser

import org.springframework.stereotype.Component

@Component
class VersionParser {

    fun parse(output: String): ParsedVersion {
        val product = fieldValue(output, "PRODUCT") ?: ""
        val version = fieldValue(output, "VERSION") ?: ""
        val patch = fieldValue(output, "PATCH")
        val uptime = fieldValue(output, "UPTIME")
        return ParsedVersion(
            product = product,
            version = version,
            patch = patch,
            uptime = uptime
        )
    }

    private fun fieldValue(output: String, key: String): String? {
        val regex = Regex("""(?i)^\s*$key\s*:\s*(.+?)\s*$""", RegexOption.MULTILINE)
        return regex.find(output)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
