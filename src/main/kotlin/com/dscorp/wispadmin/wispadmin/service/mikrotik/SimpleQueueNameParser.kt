package com.dscorp.wispadmin.wispadmin.service.mikrotik

object SimpleQueueNameParser {
    private val ID_PREFIX = Regex("^id:(\\d+)")

    fun subscriptionId(name: String?): Int? {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return ID_PREFIX.find(trimmed)?.groupValues?.get(1)?.toIntOrNull()
    }
}
