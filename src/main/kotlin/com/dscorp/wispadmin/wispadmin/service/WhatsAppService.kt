package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTemplate
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTemplateComponent
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTemplateLanguage
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTemplateMessageBody
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTemplateParameter
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTextContent
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTextMessageBody
import com.dscorp.wispadmin.wispadmin.service.whatsapp.NamedTemplateParameter
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate

@Service
class WhatsAppService(
    private val whatsAppProperties: WhatsAppProperties
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    fun sendTextMessage(
        phoneNumber: String,
        message: String
    ): Boolean {
        if (!whatsAppProperties.isConfigured()) {
            throw Exception("WhatsApp Cloud API no esta configurado correctamente.")
        }

        val body = WhatsAppTextMessageBody(
            to = normalizePhoneNumber(phoneNumber),
            text = WhatsAppTextContent(
                preview_url = false,
                body = message
            )
        )

        return postToMeta(body).success
    }

    fun sendTemplateMessageWithMetaResponse(
        phoneNumber: String,
        templateName: String,
        languageCode: String,
        parameters: List<NamedTemplateParameter>
    ): WhatsAppSendResult {
        if (!whatsAppProperties.isConfigured()) {
            throw Exception("WhatsApp Cloud API no esta configurado correctamente.")
        }

        if (templateName.isBlank()) {
            throw IllegalArgumentException("La plantilla de WhatsApp no esta configurada.")
        }

        if (languageCode.isBlank()) {
            throw IllegalArgumentException("El idioma de la plantilla de WhatsApp no esta configurado.")
        }

        if (parameters.isEmpty()) {
            throw IllegalArgumentException("La plantilla de WhatsApp requiere al menos un parametro.")
        }

        val body = WhatsAppTemplateMessageBody(
            to = normalizePhoneNumber(phoneNumber),
            template = WhatsAppTemplate(
                name = templateName,
                language = WhatsAppTemplateLanguage(code = languageCode),
                components = listOf(
                    WhatsAppTemplateComponent(
                        parameters = parameters.map { param ->
                            WhatsAppTemplateParameter(
                                parameter_name = param.parameterName,
                                text = param.text
                            )
                        }
                    )
                )
            )
        )

        return postToMeta(body)
    }

    fun sendTemplateMessage(
        phoneNumber: String,
        templateName: String,
        languageCode: String,
        parameters: List<NamedTemplateParameter>
    ): Boolean {
        return sendTemplateMessageWithMetaResponse(
            phoneNumber = phoneNumber,
            templateName = templateName,
            languageCode = languageCode,
            parameters = parameters
        ).success
    }

    private fun postToMeta(body: Any): WhatsAppSendResult {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(whatsAppProperties.accessToken.trim())

        val request = HttpEntity(body, headers)

        return try {
            val response = RestTemplate().postForEntity(
                whatsAppProperties.messagesUrl(),
                request,
                String::class.java
            )
            val responseBody = response.body ?: ""
            log.info("WhatsApp Meta API response: status={} body={}", response.statusCode.value(), responseBody)
            WhatsAppSendResult(
                success = response.statusCode.is2xxSuccessful,
                metaResponse = responseBody,
                recipient = extractRecipient(body),
                senderPhoneNumberId = whatsAppProperties.phoneNumberId
            )
        } catch (ex: HttpStatusCodeException) {
            val metaError = ex.responseBodyAsString.ifBlank { ex.message ?: "Error desconocido de Meta" }
            log.error("WhatsApp Meta API error: status={} body={}", ex.statusCode.value(), metaError)
            throw Exception("Meta API ${ex.statusCode.value()}: $metaError")
        }
    }

    private fun extractRecipient(body: Any): String? {
        return when (body) {
            is WhatsAppTextMessageBody -> body.to
            is WhatsAppTemplateMessageBody -> body.to
            else -> null
        }
    }

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
