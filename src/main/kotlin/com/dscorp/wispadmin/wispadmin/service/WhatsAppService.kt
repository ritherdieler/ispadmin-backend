package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTextContent
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTextMessageBody
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTemplate
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTemplateComponent
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTemplateLanguage
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTemplateMessageBody
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTemplateParameter

@Service
class WhatsAppService(
    private val whatsAppProperties: WhatsAppProperties
) {

    companion object {
        private val PAYMENT_REMINDER_PARAMETER_NAMES = listOf("customer_name", "amount", "billing_period")
    }

    // Envia un mensaje de texto simple usando WhatsApp Cloud API.
    fun sendTextMessage(
        phoneNumber: String,
        message: String
    ): Boolean {
        if (!whatsAppProperties.isConfigured()) {
            throw Exception("WhatsApp Cloud API no esta configurado correctamente.")
        }

        val cleanPhoneNumber = normalizePhoneNumber(phoneNumber)

        val body = WhatsAppTextMessageBody(
            to = cleanPhoneNumber,
            text = WhatsAppTextContent(
                preview_url = false,
                body = message
            )
        )

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(whatsAppProperties.accessToken)

        val request = HttpEntity(body, headers)

        val response = RestTemplate().postForEntity(
            whatsAppProperties.messagesUrl(),
            request,
            String::class.java
        )

        return response.statusCode.is2xxSuccessful
    }

    fun sendTemplateMessage(
        phoneNumber: String,
        templateName: String,
        languageCode: String,
        parameters: List<String>
    ): Boolean {
        if (!whatsAppProperties.isConfigured()) {
            throw Exception("WhatsApp Cloud API no esta configurado correctamente.")
        }

        if (templateName.isBlank()) {
            throw IllegalArgumentException("La plantilla de WhatsApp no esta configurada.")
        }

        if (languageCode.isBlank()) {
            throw IllegalArgumentException("El idioma de la plantilla de WhatsApp no esta configurado.")
        }

        if (parameters.size != PAYMENT_REMINDER_PARAMETER_NAMES.size) {
            throw IllegalArgumentException("La plantilla de recordatorio requiere ${PAYMENT_REMINDER_PARAMETER_NAMES.size} parametros.")
        }

        val cleanPhoneNumber = normalizePhoneNumber(phoneNumber)

        val body = WhatsAppTemplateMessageBody(
            to = cleanPhoneNumber,
            template = WhatsAppTemplate(
                name = templateName,
                language = WhatsAppTemplateLanguage(
                    code = languageCode
                ),
                components = listOf(
                    WhatsAppTemplateComponent(
                        parameters = parameters.zip(PAYMENT_REMINDER_PARAMETER_NAMES).map { (value, name) ->
                            WhatsAppTemplateParameter(
                                parameter_name = name,
                                text = value
                            )
                        }
                    )
                )
            )
        )

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(whatsAppProperties.accessToken)

        val request = HttpEntity(body, headers)

        val response = RestTemplate().postForEntity(
            whatsAppProperties.messagesUrl(),
            request,
            String::class.java
        )

        return response.statusCode.is2xxSuccessful
    }

    // Normaliza celulares peruanos al formato que Meta espera: 51999999999.
    private fun normalizePhoneNumber(phoneNumber: String): String {
        val digits = phoneNumber.filter { it.isDigit() }

        val normalized = when {
            digits.length == 9 && digits.startsWith("9") -> "51$digits"
            digits.length == 11 && digits.startsWith("51") -> digits
            else -> throw IllegalArgumentException("El telefono debe ser un celular peruano valido.")
        }

        if (!normalized.substring(2).startsWith("9")) {
            throw IllegalArgumentException("El telefono debe ser un celular peruano valido.")
        }

        return normalized
    }
}


