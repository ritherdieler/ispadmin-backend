package com.dscorp.wispadmin.netdiag.service

object NetDiagMonitorConfigSupport {

    const val KIND_MIKROTIK = "mikrotik"
    const val KIND_OLT = "olt"
    const val KIND_PON = "pon"

    fun kind(raw: String?): String {
        if (raw.isNullOrBlank()) return KIND_MIKROTIK
        val match = Regex(""""kind"\s*:\s*"([^"]+)"""").find(raw) ?: return KIND_MIKROTIK
        return match.groupValues[1].lowercase().ifBlank { KIND_MIKROTIK }
    }

    fun isMikrotikPollable(raw: String?): Boolean = kind(raw) == KIND_MIKROTIK
}
