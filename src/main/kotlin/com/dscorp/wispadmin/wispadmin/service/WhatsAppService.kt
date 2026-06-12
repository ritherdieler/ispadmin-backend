package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTextContent
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTextMessageBody
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class WhatsAppService(
    private val whatsAppProperties: WhatsAppProperties
) {

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

    // Limpia el numero para enviarlo en el formato que Meta espera: 51999999999.
    private fun normalizePhoneNumber(phoneNumber: String): String {
        return phoneNumber
            .replace("+", "")
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
            .trim()
    }
}