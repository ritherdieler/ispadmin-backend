package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.snmp.HuaweiGponSnmpCodec

object OnuSerialMatcher {
    fun matches(wanted: String, displayed: String): Boolean {
        val want = canonical(wanted)
        if (want.isEmpty()) return false
        if (canonical(displayed) == want) return true
        val tokens = displayed.uppercase().split(Regex("[^0-9A-Z]+")).filter { it.isNotEmpty() }
        if (tokens.any { canonical(it) == want }) return true
        val compactDisplayed = displayed.uppercase().filter { it.isLetterOrDigit() }
        val compactWanted = want.filter { it.isLetterOrDigit() }
        if (compactWanted.isNotEmpty() && compactDisplayed.contains(compactWanted)) return true
        val suffix = compactWanted.takeLast(6)
        return suffix.length == 6 &&
            suffix.all { it in '0'..'9' || it in 'A'..'F' } &&
            compactDisplayed.contains(suffix)
    }

    internal fun canonical(raw: String): String {
        val trimmed = raw.trim().uppercase()
        if (trimmed.isEmpty()) return ""
        val hexOnly = trimmed.filter { it in '0'..'9' || it in 'A'..'F' }
        if (hexOnly.length >= 16) {
            return HuaweiGponSnmpCodec.normalizeOntSn(hexOnly.take(16))
        }
        return HuaweiGponSnmpCodec.normalizeOntSn(trimmed.filter { it.isLetterOrDigit() })
    }
}
