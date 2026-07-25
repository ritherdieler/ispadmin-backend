package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppInboundMessage
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppMarkAllReadResultDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppThreadMessageDto
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppInboundMessageRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.service.WhatsAppService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppConversationQueryService.Companion.toThreadMessage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class WhatsAppConversationService(
    private val whatsAppService: WhatsAppService,
    private val subscriptionRepository: SubscriptionRepository,
    private val messageLogRepository: WhatsAppMessageLogRepository,
    private val inboundMessageRepository: WhatsAppInboundMessageRepository,
    private val serviceWindowService: WhatsAppServiceWindowService
) {

    private val log = LoggerFactory.getLogger(WhatsAppConversationService::class.java)

    fun buildAutoReplyButtons(subscription: Subscription?): List<WhatsAppService.InteractiveButtonOption> {
        return listOf(
            WhatsAppService.InteractiveButtonOption(BUTTON_DEBT, "Ver deuda"),
            WhatsAppService.InteractiveButtonOption(BUTTON_PAID, "Ya pague"),
            WhatsAppService.InteractiveButtonOption(BUTTON_SUPPORT, "Soporte")
        )
    }

    fun buildGreetingBody(subscription: Subscription?): String {
        val name = subscription?.getFullName()?.trim()?.takeIf { it.isNotBlank() && !it.contains("null") }
        return if (name != null) {
            "Hola $name, soy el asistente de GigaFiber Peru. Seleccione una opcion:"
        } else {
            "Hola, soy el asistente de GigaFiber Peru. Seleccione una opcion:"
        }
    }

    fun sendAutoReplyWithButtons(phone: String, subscription: Subscription?): AutoReplyResult {
        val bodyText = buildGreetingBody(subscription)
        return try {
            val result = whatsAppService.sendInteractiveReplyButtons(
                phoneNumber = phone,
                bodyText = bodyText,
                buttons = buildAutoReplyButtons(subscription)
            )
            AutoReplyResult(
                success = result.success,
                messageText = bodyText,
                metaMessageId = result.metaMessageId
            )
        } catch (e: Exception) {
            log.warn("Conversation: no se pudo enviar botones interactivos: ${e.message}")
            AutoReplyResult(success = false, messageText = bodyText, metaMessageId = null)
        }
    }

    fun handleButtonReply(
        phone: String,
        buttonReplyId: String?,
        subscription: Subscription?
    ): String {
        return when (buttonReplyId) {
            BUTTON_DEBT -> buildDebtResponse(subscription)
            BUTTON_PAID -> buildPaidResponse(subscription)
            BUTTON_SUPPORT -> buildSupportResponse()
            else -> buildSupportResponse()
        }
    }

    fun markInboundAsRead(inbound: WhatsAppInboundMessage): Boolean {
        val success = try {
            whatsAppService.markMessageAsRead(inbound.metaMessageId).success
        } catch (e: Exception) {
            log.warn("Conversation: mark-read fallo para ${inbound.metaMessageId}: ${e.message}")
            false
        }
        inboundMessageRepository.save(inbound.copy(readAt = LocalDateTime.now()))
        return success
    }

    fun markInboundAsRead(metaMessageId: String): Boolean {
        val inbound = inboundMessageRepository.findByMetaMessageId(metaMessageId)
            ?: return try {
                whatsAppService.markMessageAsRead(metaMessageId).success
            } catch (e: Exception) {
                log.warn("Conversation: mark-read fallo para $metaMessageId: ${e.message}")
                false
            }
        return markInboundAsRead(inbound)
    }

    fun markAllRead(phone: String): WhatsAppMarkAllReadResultDto {
        val unread = inboundMessageRepository.findByPhoneAndReadAtIsNull(phone)
        val now = LocalDateTime.now()
        var allMetaOk = true
        unread.forEach { inbound ->
            try {
                if (!whatsAppService.markMessageAsRead(inbound.metaMessageId).success) {
                    allMetaOk = false
                }
            } catch (e: Exception) {
                allMetaOk = false
                log.warn("Conversation: mark-all-read fallo para ${inbound.metaMessageId}: ${e.message}")
            }
            inboundMessageRepository.save(inbound.copy(readAt = now))
        }
        return WhatsAppMarkAllReadResultDto(
            phone = phone,
            markedCount = unread.size,
            success = allMetaOk || unread.isNotEmpty()
        )
    }

    fun sendOperatorReply(
        phone: String,
        text: String,
        operatorUsername: String?
    ): WhatsAppThreadMessageDto {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            throw IllegalArgumentException("El mensaje no puede estar vacio.")
        }
        val window = serviceWindowService.getServiceWindow(phone)
        if (!window.open) {
            throw IllegalArgumentException("La ventana de servicio de 24h esta cerrada. Solo se pueden enviar plantillas.")
        }

        val sendResult = whatsAppService.sendTextMessage(phoneNumber = phone, message = trimmed)
        if (!sendResult.success) {
            throw Exception(sendResult.metaResponse.ifBlank { "No se pudo enviar el mensaje por WhatsApp." })
        }

        val subscription = findSubscriptionByPhone(phone)
        val now = LocalDateTime.now()
        val saved = messageLogRepository.save(
            WhatsAppMessageLog(
                subscriptionId = subscription?.id,
                phone = phone,
                messageType = MESSAGE_TYPE_OPERATOR_REPLY,
                status = "SENT",
                metaMessageId = sendResult.metaMessageId,
                message = trimmed,
                operatorUsername = operatorUsername,
                sentAt = now,
                createdAt = now
            )
        )
        return saved.toThreadMessage()
    }

    fun resolveReplyToLogId(contextMessageId: String?): Int? {
        if (contextMessageId.isNullOrBlank()) return null
        return messageLogRepository.findByMetaMessageId(contextMessageId)?.id
    }

    fun findSubscriptionByPhone(phone: String): Subscription? {
        val digits = phone.filter { it.isDigit() }
        val normalized = when {
            digits.length == 11 && digits.startsWith("51") -> digits.substring(2)
            digits.length == 9 && digits.startsWith("9") -> digits
            else -> digits
        }
        return subscriptionRepository.findByNormalizedPhone(normalized).firstOrNull()
    }

    private fun buildDebtResponse(subscription: Subscription?): String {
        if (subscription == null) {
            return "No encontramos su cuenta. Comuniquese con atencion al cliente."
        }
        val pending = subscription.payments.filter { !it.paid }
        if (pending.isEmpty()) {
            return "No encontramos facturas pendientes en su cuenta."
        }
        val total = pending.sumOf { it.amountToPay }
        val count = pending.size
        val word = if (count == 1) "factura" else "facturas"
        return "Tiene $count $word pendiente(s) por un total de S/ ${"%.2f".format(total)}."
    }

    private fun buildPaidResponse(subscription: Subscription?): String {
        return if (subscription != null) {
            "Gracias por informarnos. Verificaremos su pago y le confirmaremos a la brevedad."
        } else {
            "Gracias por informarnos. Un asesor revisara su mensaje."
        }
    }

    private fun buildSupportResponse(): String {
        return "Para atencion personalizada comuniquese con nuestro equipo de atencion al cliente. Gracias."
    }

    data class AutoReplyResult(
        val success: Boolean,
        val messageText: String,
        val metaMessageId: String?
    )

    companion object {
        const val BUTTON_DEBT = "ver_deuda"
        const val BUTTON_PAID = "ya_pague"
        const val BUTTON_SUPPORT = "soporte"
        const val MESSAGE_TYPE_AUTO_REPLY = "AUTO_REPLY"
        const val MESSAGE_TYPE_OPERATOR_REPLY = "OPERATOR_REPLY"
    }
}
