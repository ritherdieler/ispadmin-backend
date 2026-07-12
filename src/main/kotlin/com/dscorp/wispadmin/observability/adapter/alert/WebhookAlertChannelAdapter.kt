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
class WebhookAlertChannelAdapter : AlertChannelPort {

    private val log = LoggerFactory.getLogger(this::class.java)
    private val restTemplate = RestTemplate()

    override val channelType: ObsAlertChannelType = ObsAlertChannelType.WEBHOOK

    override fun send(channel: ObsAlertChannel, notification: AlertNotification): AlertChannelSendResult {
        if (channel.target.isBlank()) {
            return AlertChannelSendResult(false, "URL de webhook no configurada")
        }
        return try {
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            val body = mapOf(
                "title" to notification.title,
                "message" to notification.message,
                "severity" to notification.severity,
                "type" to notification.type,
                "issueUrl" to notification.issueUrl
            )
            restTemplate.postForEntity(channel.target, HttpEntity(body, headers), String::class.java)
            AlertChannelSendResult(true, "Webhook entregado")
        } catch (e: HttpStatusCodeException) {
            log.warn("Error enviando alerta a webhook: {} {}", e.rawStatusCode, e.responseBodyAsString.take(200))
            AlertChannelSendResult(false, "Webhook error ${e.rawStatusCode}")
        } catch (e: Exception) {
            log.warn("Error enviando alerta a webhook: {}", e.message)
            AlertChannelSendResult(false, "Webhook: ${e.message}")
        }
    }
}
