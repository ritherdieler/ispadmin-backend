package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.dto.AssistanceTicketDto
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class TicketNotificationService(
    private val messagingTemplate: SimpMessagingTemplate
) {

    fun notifyTicketStatusChange(
        ticketId: Long,
        oldStatus: String,
        newStatus: String,
        assignedTo: String? = null
    ) {
        val event = TicketStatusChangeEvent(
            ticketId = ticketId,
            oldStatus = oldStatus,
            newStatus = newStatus,
            timestamp = LocalDateTime.now().toString(),
            assignedTo = assignedTo
        )

        val message = WebSocketMessage(
            type = "TICKET_STATUS_CHANGE",
            data = event
        )

        // Enviar a todos los clientes suscritos al topic de tickets
        messagingTemplate.convertAndSend("/topic/tickets", message)
    }

    fun notifyTicketCreated(ticket: AssistanceTicketDto) {
        val message = WebSocketMessage(
            type = "TICKET_CREATED",
            data = ticket
        )

        messagingTemplate.convertAndSend("/topic/tickets", message)
    }

    fun notifyTicketAssigned(
        ticketId: Long,
        oldStatus: String,
        newStatus: String,
        assignedTo: String
    ) {
        val event = TicketStatusChangeEvent(
            ticketId = ticketId,
            oldStatus = oldStatus,
            newStatus = newStatus,
            timestamp = LocalDateTime.now().toString(),
            assignedTo = assignedTo
        )

        val message = WebSocketMessage(
            type = "TICKET_ASSIGNED",
            data = event
        )

        messagingTemplate.convertAndSend("/topic/tickets", message)
    }

    fun notifyTicketClosed(
        ticketId: Long,
        oldStatus: String,
        newStatus: String,
        assignedTo: String? = null
    ) {
        val event = TicketStatusChangeEvent(
            ticketId = ticketId,
            oldStatus = oldStatus,
            newStatus = newStatus,
            timestamp = LocalDateTime.now().toString(),
            assignedTo = assignedTo
        )

        val message = WebSocketMessage(
            type = "TICKET_CLOSED",
            data = event
        )

        messagingTemplate.convertAndSend("/topic/tickets", message)
    }
}

data class TicketStatusChangeEvent(
    val ticketId: Long,
    val oldStatus: String,
    val newStatus: String,
    val timestamp: String,
    val assignedTo: String? = null
)

data class WebSocketMessage(
    val type: String,
    val data: Any
) 