package com.dscorp.wispadmin.oltgateway.parser

import org.springframework.stereotype.Component

@Component
class OnuVersionParser {
    private val vendorIdRegex = Regex("""(?im)^\\s*Vendor-ID\\s*:\\s*(\\S+)\\s*$""")

    fun parseVendorId(output: String): String? =
        vendorIdRegex.find(output)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
}
