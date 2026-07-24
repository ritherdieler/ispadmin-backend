package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppInboundMessage
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppInboundMessageRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class WhatsAppInboundMessageService(
    private val inboundMessageRepository: WhatsAppInboundMessageRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val whatsAppService: WhatsAppService
) {

    private val log = LoggerFactory.getLogger(WhatsAppInboundMessageService::class.java)

    fun processInboundMessage(
        metaMessageId: String,
        phone: String,
        messageType: String,
        messageText: String?
    ) {
        val saved = inboundMessageRepository.save(
            WhatsAppInboundMessage(
                metaMessageId = metaMessageId,
                phone = phone,
                messageText = messageText,
                messageType = messageType,
                processed = false,
                replySent = false
            )
        )

        if (messageType != "text") {
            inboundMessageRepository.save(saved.copy(processed = true, messageType = "UNSUPPORTED"))
            return
        }

        val subscription = findSubscriptionByPhone(phone)

        val replyText = if (subscription != null) {
            val name = subscription.getFullName().trim()
            val pendingPayments = subscription.payments.filter { !it.paid }
            if (pendingPayments.isNotEmpty()) {
                val total = pendingPayments.sumOf { it.amountToPay }
                val count = pendingPayments.size
                val word = if (count == 1) "factura" else "facturas"
                "Hola $name, soy el asistente de GigaFiber Peru.\n\n" +
                "Tiene $count $word pendiente(s) por un total de S/ ${"%.2f".format(total)}.\n\n" +
                "Para gestionar su pago comuniquese con nuestro equipo de atencion al cliente. Gracias."
            } else {
                "Hola $name, soy el asistente de GigaFiber Peru.\n\n" +
                "No encontramos facturas pendientes en su cuenta.\n\n" +
                "Si necesita ayuda comuniquese con nuestro equipo de atencion al cliente. Gracias."
            }
        } else {
            "Hola, soy el asistente de GigaFiber Peru.\n\n" +
            "Recibimos su mensaje. Para atencion personalizada comuniquese con nosotros " +
            "por nuestros canales de atencion al cliente.\n\n" +
            "Gracias por contactarnos."
        }

        var replySent = false
        var errorMsg: String? = null

        try {
            whatsAppService.sendTextMessage(phoneNumber = phone, message = replyText)
            replySent = true
        } catch (e: Exception) {
            log.warn("Inbound: no se pudo responder al mensaje $metaMessageId: ${e.message}")
            errorMsg = e.message?.take(1000)
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

    private fun findSubscriptionByPhone(phone: String): com.dscorp.wispadmin.wispadmin.data.model.Subscription? {
        val digits = phone.filter { it.isDigit() }
        val normalized = when {
            digits.length == 11 && digits.startsWith("51") -> digits.substring(2)
            digits.length == 9 && digits.startsWith("9") -> digits
            else -> digits
        }
        return subscriptionRepository.findByNormalizedPhone(normalized).firstOrNull()
    }
}
