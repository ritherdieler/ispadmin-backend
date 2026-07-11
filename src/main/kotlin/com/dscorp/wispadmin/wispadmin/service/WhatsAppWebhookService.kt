package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppWebhookEvent
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppWebhookEventRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class WhatsAppWebhookService(
    private val whatsAppProperties: WhatsAppProperties,
    private val whatsAppMessageLogRepository: WhatsAppMessageLogRepository,
    private val whatsAppWebhookEventRepository: WhatsAppWebhookEventRepository,
    private val whatsAppInboundMessageService: WhatsAppInboundMessageService
) {

    private val log = LoggerFactory.getLogger(WhatsAppWebhookService::class.java)
    private val objectMapper = ObjectMapper()
    private val webhookZone = ZoneId.of("America/Lima")

    fun verifySignature(payload: ByteArray, signatureHeader: String?): Boolean {
        if (!whatsAppProperties.isWebhookConfigured()) return false
        if (signatureHeader.isNullOrBlank()) return false

        val expected = signatureHeader.removePrefix("sha256=")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(whatsAppProperties.appSecret.toByteArray(), "HmacSHA256"))
        val computed = mac.doFinal(payload).joinToString("") { "%02x".format(it) }
        if (expected.length != computed.length) return false
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            computed.toByteArray(Charsets.UTF_8)
        )
    }

    @Async
    fun processPayloadAsync(rawBody: String) {
        try {
            processPayload(rawBody)
        } catch (e: Exception) {
            log.error("Webhook: error procesando payload async: ${e.message}", e)
        }
    }

    fun processPayload(rawBody: String) {
        val root: JsonNode = try {
            objectMapper.readTree(rawBody)
        } catch (e: Exception) {
            log.warn("Webhook: payload no es JSON valido")
            return
        }

        if (root.path("object").asText(null) != "whatsapp_business_account") {
            log.debug("Webhook: object ignorado ({})", root.path("object").asText(null))
            return
        }

        root.path("entry").forEach { entry ->
            entry.path("changes").forEach { change ->
                if (change.path("field").asText(null) != "messages") return@forEach

                val value = change.path("value")

                value.path("statuses").forEach { status ->
                    processStatusEvent(status)
                }

                value.path("messages").forEach { message ->
                    processMessageEvent(message)
                }
            }
        }
    }

    private fun processStatusEvent(status: JsonNode) {
        val wamid = status.path("id").asText(null) ?: return
        val deliveryStatus = status.path("status").asText(null) ?: return
        val timestamp = status.path("timestamp").asText("")
        val eventKey = "status:$wamid:$deliveryStatus:$timestamp"

        if (whatsAppWebhookEventRepository.existsByEventKey(eventKey)) return

        whatsAppWebhookEventRepository.save(
            WhatsAppWebhookEvent(
                eventKey = eventKey,
                eventType = "status",
                payloadSummary = status.toString().take(4000)
            )
        )

        val messageLog = whatsAppMessageLogRepository.findByMetaMessageId(wamid) ?: return
        messageLog.deliveryStatus = deliveryStatus
        messageLog.deliveryStatusAt = parseWebhookTimestamp(timestamp) ?: LocalDateTime.now()
        if (deliveryStatus == "failed") {
            val errorTitle = status.path("errors").path(0).path("title").asText(null)
            if (!errorTitle.isNullOrBlank()) {
                messageLog.errorMessage = errorTitle.take(1000)
            }
        }
        whatsAppMessageLogRepository.save(messageLog)
    }

    private fun processMessageEvent(message: JsonNode) {
        val wamid = message.path("id").asText(null) ?: return
        val eventKey = "message:$wamid"

        if (whatsAppWebhookEventRepository.existsByEventKey(eventKey)) return

        whatsAppWebhookEventRepository.save(
            WhatsAppWebhookEvent(
                eventKey = eventKey,
                eventType = "message",
                payloadSummary = message.toString().take(4000)
            )
        )

        val phone = message.path("from").asText(null) ?: return
        val messageType = message.path("type").asText("text")

        val messageText = when (messageType) {
            "text" -> message.path("text").path("body").asText(null)
            else -> null
        }

        whatsAppInboundMessageService.processInboundMessage(
            metaMessageId = wamid,
            phone = phone,
            messageType = messageType,
            messageText = messageText
        )
    }

    private fun parseWebhookTimestamp(timestamp: String): LocalDateTime? {
        if (timestamp.isBlank()) return null
        return try {
            LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp.toLong()), webhookZone)
        } catch (_: Exception) {
            null
        }
    }
}
