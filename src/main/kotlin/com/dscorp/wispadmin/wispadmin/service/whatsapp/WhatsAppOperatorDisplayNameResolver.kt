package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.dto.WhatsAppThreadMessageDto
import com.dscorp.wispadmin.wispadmin.repository.UserRepository
import org.springframework.stereotype.Service

@Service
class WhatsAppOperatorDisplayNameResolver(
    private val userRepository: UserRepository,
) {

    fun enrichMessages(messages: List<WhatsAppThreadMessageDto>): List<WhatsAppThreadMessageDto> {
        val usernames = messages
            .mapNotNull { it.operatorUsername?.trim()?.takeIf { name -> name.isNotEmpty() } }
            .distinct()
        if (usernames.isEmpty()) return messages
        val lookup = WhatsAppOperatorDisplayNames.buildLookup(
            userRepository.findByUsernameLowerIn(usernames.map { it.lowercase() }),
        )
        return WhatsAppOperatorDisplayNames.enrichMessages(messages, lookup)
    }

    fun enrichMessage(message: WhatsAppThreadMessageDto): WhatsAppThreadMessageDto =
        enrichMessages(listOf(message)).first()
}
