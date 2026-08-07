package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.CrmEventLog
import com.dscorp.wispadmin.wispadmin.dto.CrmRealtimeEventDto
import com.dscorp.wispadmin.wispadmin.repository.CrmEventLogRepository
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class CrmEventPublisher(
    private val repository: CrmEventLogRepository,
    private val messagingTemplate: SimpMessagingTemplate,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(CrmEventPublisher::class.java)

    fun publish(eventType: String, payload: Map<String, Any?>): CrmRealtimeEventDto {
        val createdAt = LocalDateTime.now()
        val payloadJson = objectMapper.writeValueAsString(payload)
        val saved = repository.save(
            CrmEventLog(
                eventType = eventType,
                payload = payloadJson,
                createdAt = createdAt
            )
        )
        val eventId = requireNotNull(saved.id) { "CrmEventLog id must be generated" }
        val dto = CrmRealtimeEventDto(
            eventId = eventId,
            eventType = eventType,
            payload = payload,
            createdAt = saved.createdAt.toString()
        )
        try {
            messagingTemplate.convertAndSend(TOPIC_WHATSAPP, dto)
        } catch (e: Exception) {
            log.warn("CRM event {} persisted but STOMP publish failed: {}", eventId, e.message)
        }
        return dto
    }

    fun findSince(sinceEventId: Long): List<CrmRealtimeEventDto> {
        return repository.findByIdGreaterThanOrderByIdAsc(sinceEventId.coerceAtLeast(0L))
            .take(MAX_CATCH_UP)
            .map { it.toDto() }
    }

    private fun CrmEventLog.toDto(): CrmRealtimeEventDto {
        val parsed = try {
            objectMapper.readValue(payload, object : TypeReference<Map<String, Any?>>() {})
        } catch (_: Exception) {
            emptyMap()
        }
        return CrmRealtimeEventDto(
            eventId = requireNotNull(id),
            eventType = eventType,
            payload = parsed,
            createdAt = createdAt.toString()
        )
    }

    companion object {
        const val TOPIC_WHATSAPP = "/topic/whatsapp"
        const val MESSAGE_RECEIVED = "MESSAGE_RECEIVED"
        const val MESSAGE_STATUS = "MESSAGE_STATUS"
        const val MESSAGE_REACTION = "MESSAGE_REACTION"
        const val MESSAGE_UPDATED = "MESSAGE_UPDATED"
        const val CONVERSATION_UPDATED = "CONVERSATION_UPDATED"
        const val BATCH_PROGRESS = "BATCH_PROGRESS"
        private const val MAX_CATCH_UP = 500
    }
}
