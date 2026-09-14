package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.User
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppThreadMessageDto

object WhatsAppOperatorDisplayNames {

    fun formatUser(user: User): String {
        return listOfNotNull(
            user.name?.trim()?.takeIf { it.isNotEmpty() },
            user.lastName?.trim()?.takeIf { it.isNotEmpty() },
        ).joinToString(" ")
    }

    fun buildLookup(users: List<User>): Map<String, User> {
        val map = LinkedHashMap<String, User>()
        for (user in users) {
            val key = user.username?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: continue
            map.putIfAbsent(key, user)
        }
        return map
    }

    fun resolveDisplayName(username: String?, lookup: Map<String, User>): String? {
        val key = username?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        val user = lookup[key] ?: return null
        val formatted = formatUser(user)
        return formatted.takeIf { it.isNotBlank() }
    }

    fun enrichMessages(
        messages: List<WhatsAppThreadMessageDto>,
        lookup: Map<String, User>,
    ): List<WhatsAppThreadMessageDto> =
        messages.map { message ->
            val displayName = resolveDisplayName(message.operatorUsername, lookup) ?: return@map message
            message.copy(operatorDisplayName = displayName)
        }
}
