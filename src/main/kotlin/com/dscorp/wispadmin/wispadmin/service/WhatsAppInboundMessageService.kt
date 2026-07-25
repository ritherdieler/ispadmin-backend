package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppInboundMessage
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppInboundMessageRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppConversationService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppInboundPayload
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppMediaDownloadService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class WhatsAppInboundMessageService(
    private val inboundMessageRepository: WhatsAppInboundMessageRepository,
    private val conversationService: WhatsAppConversationService,
    private val mediaDownloadService: WhatsAppMediaDownloadService,
    private val whatsAppService: WhatsAppService,
    private val messageLogRepository: WhatsAppMessageLogRepository
) {

    private val log = LoggerFactory.getLogger(WhatsAppInboundMessageService::class.java)

    fun processInboundMessage(payload: WhatsAppInboundPayload) {
        val replyToLogId = conversationService.resolveReplyToLogId(payload.contextMessageId)
        val mediaStoredPath = payload.mediaId?.let {
            mediaDownloadService.downloadAndStore(it, payload.mediaMimeType)
        }

        val saved = inboundMessageRepository.save(
            WhatsAppInboundMessage(
                metaMessageId = payload.metaMessageId,
                phone = payload.phone,
                messageText = payload.messageText,
                messageType = payload.messageType,
                buttonReplyId = payload.buttonReplyId,
                buttonReplyTitle = payload.buttonReplyTitle,
                mediaId = payload.mediaId,
                mediaMimeType = payload.mediaMimeType,
                mediaStoredPath = mediaStoredPath,
                contextMessageId = payload.contextMessageId,
                replyToLogId = replyToLogId,
                processed = false,
                replySent = false
            )
        )

        val subscription = conversationService.findSubscriptionByPhone(payload.phone)

        val (replySent, errorMsg) = when (payload.messageType) {
            "text" -> {
                val result = conversationService.sendAutoReplyWithButtons(payload.phone, subscription)
                if (result.success) {
                    persistAutoReplyLog(
                        phone = payload.phone,
                        subscriptionId = subscription?.id,
                        message = result.messageText,
                        metaMessageId = result.metaMessageId
                    )
                }
                Pair(result.success, if (result.success) null else "No se pudo enviar respuesta interactiva.")
            }
            "button_reply" -> {
                val text = conversationService.handleButtonReply(
                    phone = payload.phone,
                    buttonReplyId = payload.buttonReplyId,
                    subscription = subscription
                )
                sendTextReply(payload.phone, text, subscription?.id)
            }
            "image", "document" -> sendTextReply(
                payload.phone,
                "Recibimos su comprobante. Nuestro equipo lo revisara a la brevedad. Gracias.",
                subscription?.id
            )
            else -> Pair(false, null)
        }

        inboundMessageRepository.save(
            saved.copy(
                subscriptionId = subscription?.id,
                processed = true,
                replySent = replySent,
                errorMessage = errorMsg
            )
        )
    }

    private fun sendTextReply(
        phone: String,
        text: String,
        subscriptionId: Int?
    ): Pair<Boolean, String?> {
        return try {
            val result = whatsAppService.sendTextMessage(phoneNumber = phone, message = text)
            if (result.success) {
                persistAutoReplyLog(
                    phone = phone,
                    subscriptionId = subscriptionId,
                    message = text,
                    metaMessageId = result.metaMessageId
                )
                Pair(true, null)
            } else {
                Pair(false, result.metaResponse.take(1000).ifBlank { "No se pudo enviar respuesta." })
            }
        } catch (e: Exception) {
            log.warn("Inbound: no se pudo responder al telefono $phone: ${e.message}")
            Pair(false, e.message?.take(1000))
        }
    }

    private fun persistAutoReplyLog(
        phone: String,
        subscriptionId: Int?,
        message: String,
        metaMessageId: String?
    ) {
        val now = LocalDateTime.now()
        messageLogRepository.save(
            WhatsAppMessageLog(
                subscriptionId = subscriptionId,
                phone = phone,
                messageType = WhatsAppConversationService.MESSAGE_TYPE_AUTO_REPLY,
                status = "SENT",
                metaMessageId = metaMessageId,
                message = message,
                sentAt = now,
                createdAt = now
            )
        )
    }
}
