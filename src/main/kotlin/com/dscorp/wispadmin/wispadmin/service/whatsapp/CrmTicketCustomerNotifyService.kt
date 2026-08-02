package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicket
import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicketStatus
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.service.WhatsAppService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class CrmTicketCustomerNotifyService(
    private val whatsAppService: WhatsAppService,
    private val serviceWindowService: WhatsAppServiceWindowService,
    private val messageLogRepository: WhatsAppMessageLogRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun notifyStatusChange(
        ticket: AssistanceTicket,
        oldStatus: String,
        newStatus: String,
        assignedTo: String?
    ): Boolean {
        val phone = ticket.phone.trim()
        if (phone.isBlank()) return false
        if (newStatus !in NOTIFY_STATUSES) return false
        if (oldStatus == newStatus) return false

        val window = serviceWindowService.getServiceWindow(phone)
        if (!window.open) {
            log.info(
                "Ticket WhatsApp notify skipped (ventana 24h cerrada, plantilla APPROVED pendiente): ticketId={} status={}",
                ticket.id,
                newStatus
            )
            return false
        }

        val statusLabel = runCatching { AssistanceTicketStatus.valueOf(newStatus).status }
            .getOrElse { newStatus }
        val assigneeLine = assignedTo?.takeIf { it.isNotBlank() }?.let { "\nTécnico: $it" }.orEmpty()
        val body = """
            |Actualización de su ticket #${ticket.id}:
            |Estado: $statusLabel$assigneeLine
            |
            |Categoría: ${ticket.category}
            |Gracias por su paciencia.
        """.trimMargin()

        return try {
            val result = whatsAppService.sendTextMessage(phoneNumber = phone, message = body)
            if (!result.success) return false
            try {
                messageLogRepository.save(
                    WhatsAppMessageLog(
                        paymentId = null,
                        subscriptionId = ticket.subscription?.id,
                        phone = phone,
                        messageType = MESSAGE_TYPE,
                        status = "SENT",
                        message = body,
                        metaMessageId = result.metaMessageId,
                        sentAt = LocalDateTime.now(),
                        operatorUsername = "system"
                    )
                )
            } catch (e: Exception) {
                log.warn("Ticket WhatsApp notify log failed for ticket {}: {}", ticket.id, e.message)
            }
            true
        } catch (e: Exception) {
            log.warn("Ticket WhatsApp notify failed for ticket {}: {}", ticket.id, e.message)
            false
        }
    }

    companion object {
        const val MESSAGE_TYPE = "TICKET_STATUS_UPDATE"
        private val NOTIFY_STATUSES = setOf(
            AssistanceTicketStatus.ASSIGNED.name,
            AssistanceTicketStatus.IN_PROGRESS.name,
            AssistanceTicketStatus.RESOLVED.name,
            AssistanceTicketStatus.REOPEN.name,
            AssistanceTicketStatus.CLOSED.name
        )
    }
}
