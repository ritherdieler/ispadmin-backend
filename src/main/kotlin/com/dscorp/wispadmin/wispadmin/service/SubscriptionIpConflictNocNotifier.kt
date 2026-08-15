package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import com.dscorp.wispadmin.wispadmin.service.whatsapp.NamedTemplateParameter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class SubscriptionIpConflictNocNotifier(
    private val whatsAppService: WhatsAppService,
    @Value("\${net.diag.whatsapp.noc-phone:}") private val nocPhone: String,
    @Value("\${net.diag.whatsapp.template-name:noc_alert_v1}") private val templateName: String,
    @Value("\${net.diag.whatsapp.language-code:es}") private val languageCode: String
) {

    private val logger = LoggerFactory.getLogger(SubscriptionIpConflictNocNotifier::class.java)

    fun notifyIpConflict(request: SubscriptionRequest) {
        val phone = nocPhone.trim()
        if (phone.isEmpty()) {
            logger.warn(
                "IP_CONFLICT sin alerta NOC: net.diag.whatsapp.noc-phone vacio. ip={}, clientRequestId={}",
                request.clientIpAddress,
                request.clientRequestId
            )
            return
        }

        val ip = request.clientIpAddress?.trim().orEmpty().ifBlank { "n/a" }
        val clientLabel = "${request.firstName} ${request.lastName} DNI ${request.dni}".take(60)
        val target = "IP $ip | $clientLabel | req=${request.clientRequestId ?: "n/a"} | host=${request.hostDeviceId}"
            .take(60)

        val parameters = listOf(
            NamedTemplateParameter("severity", "HIGH"),
            NamedTemplateParameter("title", "Colision IP sync offline".take(60)),
            NamedTemplateParameter("reason_code", ERROR_CODE),
            NamedTemplateParameter("target", target)
        )

        try {
            val sent = whatsAppService.sendTemplateMessage(
                phoneNumber = phone,
                templateName = templateName,
                languageCode = languageCode,
                parameters = parameters
            )
            if (!sent) {
                logger.warn("IP_CONFLICT alerta NOC no enviada (sendTemplateMessage=false) ip={}", ip)
            }
        } catch (ex: Exception) {
            logger.warn("IP_CONFLICT alerta NOC fallo ip={}: {}", ip, ex.message)
        }
    }

    companion object {
        const val ERROR_CODE = "IP_CONFLICT"
        const val ERROR_MESSAGE =
            "La IP del cliente ya esta en uso. Coordina otra IP con el equipo e intenta de nuevo."
    }
}
