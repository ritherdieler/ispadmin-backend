package com.dscorp.wispadmin.observability.adapter.alert

import com.dscorp.wispadmin.observability.entity.ObsAlertChannel
import com.dscorp.wispadmin.observability.entity.ObsAlertChannelType
import com.dscorp.wispadmin.observability.port.AlertChannelPort
import com.dscorp.wispadmin.observability.port.AlertChannelSendResult
import com.dscorp.wispadmin.observability.port.AlertNotification
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate

@Component
class SlackAlertChannelAdapter : AlertChannelPort {

    private val log = LoggerFactory.getLogger(this::class.java)
    private val restTemplate = RestTemplate()

    override val channelType: ObsAlertChannelType = ObsAlertChannelType.SLACK

    override fun send(channel: ObsAlertChannel, notification: AlertNotification): AlertChannelSendResult {
        if (channel.target.isBlank()) {
            return AlertChannelSendResult(false, "Webhook de Slack no configurado")
        }
        return try {
            val emoji = when (notification.severity?.lowercase()) {
                "fatal", "error" -> ":red_circle:"
                "warning" -> ":large_yellow_circle:"
                else -> ":large_blue_circle:"
            }
            val lines = buildString {
                append("$emoji *${notification.title}*\n")
                append(notification.message)
                notification.issueUrl?.let { append("\n<$it|Ver detalle>") }
            }
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            val body = mapOf("text" to lines)
            restTemplate.postForEntity(channel.target, HttpEntity(body, headers), String::class.java)
            AlertChannelSendResult(true, "Enviado a Slack")
        } catch (e: HttpStatusCodeException) {
            log.warn("Error enviando alerta a Slack: {} {}", e.rawStatusCode, e.responseBodyAsString.take(200))
            AlertChannelSendResult(false, "Slack error ${e.rawStatusCode}")
        } catch (e: Exception) {
            log.warn("Error enviando alerta a Slack: {}", e.message)
            AlertChannelSendResult(false, "Slack: ${e.message}")
        }
    }
}
