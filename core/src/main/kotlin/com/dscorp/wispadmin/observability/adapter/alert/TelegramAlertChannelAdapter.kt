package com.dscorp.wispadmin.observability.adapter.alert

import com.dscorp.wispadmin.observability.entity.ObsAlertChannel
import com.dscorp.wispadmin.observability.entity.ObsAlertChannelType
import com.dscorp.wispadmin.observability.port.AlertChannelPort
import com.dscorp.wispadmin.observability.port.AlertChannelSendResult
import com.dscorp.wispadmin.observability.port.AlertNotification
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate

@Component
class TelegramAlertChannelAdapter(
    private val objectMapper: ObjectMapper
) : AlertChannelPort {

    private val log = LoggerFactory.getLogger(this::class.java)
    private val restTemplate = RestTemplate()

    override val channelType: ObsAlertChannelType = ObsAlertChannelType.TELEGRAM

    override fun send(channel: ObsAlertChannel, notification: AlertNotification): AlertChannelSendResult {
        val config = parseConfig(channel.configJson)
        val botToken = config?.get("botToken")?.asText().orEmpty()
        val chatId = channel.target.ifBlank { config?.get("chatId")?.asText().orEmpty() }
        if (botToken.isBlank() || chatId.isBlank()) {
            return AlertChannelSendResult(false, "Telegram requiere botToken (config) y chatId (destino)")
        }
        return try {
            val text = buildString {
                append("⚠️ ${notification.title}\n")
                append(notification.message)
                notification.issueUrl?.let { append("\n$it") }
            }
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            val body = mapOf("chat_id" to chatId, "text" to text, "disable_web_page_preview" to true)
            val url = "https://api.telegram.org/bot$botToken/sendMessage"
            restTemplate.postForEntity(url, HttpEntity(body, headers), String::class.java)
            AlertChannelSendResult(true, "Enviado a Telegram")
        } catch (e: HttpStatusCodeException) {
            log.warn("Error enviando alerta a Telegram: {} {}", e.rawStatusCode, e.responseBodyAsString.take(200))
            AlertChannelSendResult(false, "Telegram error ${e.rawStatusCode}")
        } catch (e: Exception) {
            log.warn("Error enviando alerta a Telegram: {}", e.message)
            AlertChannelSendResult(false, "Telegram: ${e.message}")
        }
    }

    private fun parseConfig(json: String?): JsonNode? {
        if (json.isNullOrBlank()) return null
        return try {
            objectMapper.readTree(json)
        } catch (e: Exception) {
            null
        }
    }
}
