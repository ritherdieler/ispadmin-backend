package com.dscorp.wispadmin.wispadmin.service.mikrotik

data class QueueOwner(val envTag: String, val subscriptionId: Int?)

object SimpleQueueNameParser {
    private val OWNER = Regex("^(?:\\[([a-zA-Z0-9]+)\\]\\s+)?id:(\\d+)")

    fun owner(name: String?): QueueOwner? {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val match = OWNER.find(trimmed) ?: return null
        val subscriptionId = match.groupValues[2].toIntOrNull() ?: return null
        val envTag = match.groupValues[1]
        return QueueOwner(envTag = envTag, subscriptionId = subscriptionId)
    }

    fun subscriptionId(name: String?): Int? {
        return owner(name)?.subscriptionId
    }

    fun belongsToEnvironment(name: String?, envTag: String): Boolean {
        val owner = owner(name) ?: return envTag.isEmpty()
        return owner.envTag == envTag
    }
}
