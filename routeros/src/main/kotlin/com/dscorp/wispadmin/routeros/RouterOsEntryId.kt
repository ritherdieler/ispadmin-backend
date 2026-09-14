package com.dscorp.wispadmin.routeros

object RouterOsEntryId {

    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        return if (trimmed.startsWith("*")) trimmed else "*$trimmed"
    }
}
