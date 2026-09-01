package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.service.whatsapp.PeruvianWhatsAppPhone
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppInteractiveAction
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppInteractiveActionButton
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppInteractiveButton
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppInteractiveContent
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppInteractiveListAction
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppInteractiveListContent
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppInteractiveListReplyBody
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppInteractiveListRow
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppInteractiveListSection
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppInteractiveReplyBody
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppInteractiveText
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppMarkReadBody
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppMediaMessageBody
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppMessageContext
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppPassThreadControlBody
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppReactionMessageBody
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTemplate
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTemplateComponent
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTemplateLanguage
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTemplateMessageBody
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTemplateParameter
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTakeThreadControlBody
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTextContent
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTextMessageBody
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppThreadControlRecipient
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppTypingIndicator
import com.dscorp.wispadmin.wispadmin.service.whatsapp.NamedTemplateParameter
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppInteractiveListOption
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppInteractiveMessageValidator
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppInteractiveOption
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppInteractiveSection
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppMediaMessagePayloadBuilder
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppMetaResponseParser
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppOutboundMediaKind
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppReactionPayloadBuilder
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateButtonParameter
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import com.dscorp.wispadmin.wispadmin.tracing.TracingInterceptorHolder
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate

@Service
class WhatsAppService(
    private val whatsAppProperties: WhatsAppProperties,
) {

    private val log = LoggerFactory.getLogger(this::class.java)
    private var restTemplate: RestTemplate = RestTemplate().apply {
        TracingInterceptorHolder.instance?.let { interceptors.add(it) }
    }
    private val objectMapper = ObjectMapper()

    /** Replaces the HTTP client for unit tests backed by MockRestServiceServer. */
    internal fun useRestTemplate(template: RestTemplate) {
        restTemplate = template
    }

    fun sendTextMessage(
        phoneNumber: String,
        message: String,
        contextMessageId: String? = null
    ): WhatsAppSendResult {
        if (!whatsAppProperties.isConfigured()) {
            throw Exception("WhatsApp Cloud API no esta configurado correctamente.")
        }

        val body = WhatsAppTextMessageBody(
            to = normalizePhoneNumber(phoneNumber),
            text = WhatsAppTextContent(
                preview_url = false,
                body = message
            ),
            context = contextMessageId?.takeIf { it.isNotBlank() }?.let { WhatsAppMessageContext(it) }
        )

        return postToMeta(body)
    }

    /**
     * Sends (or clears) an emoji reaction on a WhatsApp user message.
     * Pass [emoji] as empty string to remove an existing reaction (Meta contract).
     */
    fun sendReaction(
        phoneNumber: String,
        wamid: String,
        emoji: String,
    ): WhatsAppSendResult {
        if (!whatsAppProperties.isConfigured()) {
            throw Exception("WhatsApp Cloud API no esta configurado correctamente.")
        }
        val body = WhatsAppReactionPayloadBuilder.build(
            phoneNumber = phoneNumber,
            wamid = wamid,
            emoji = emoji,
            normalizePhone = ::normalizePhoneNumber,
        )
        return postToMeta(body)
    }

    fun uploadMedia(
        bytes: ByteArray,
        mimeType: String,
        filename: String
    ): String {
        if (!whatsAppProperties.isConfigured()) {
            throw Exception("WhatsApp Cloud API no esta configurado correctamente.")
        }
        val headers = HttpHeaders()
        headers.setBearerAuth(whatsAppProperties.accessToken.trim())
        headers.contentType = MediaType.MULTIPART_FORM_DATA

        val fileResource = object : ByteArrayResource(bytes) {
            override fun getFilename(): String = filename.ifBlank { "file" }
        }
        val parts = LinkedMultiValueMap<String, Any>()
        parts.add("messaging_product", "whatsapp")
        parts.add("type", mimeType)
        val fileHeaders = HttpHeaders()
        fileHeaders.contentType = MediaType.parseMediaType(mimeType)
        parts.add("file", HttpEntity(fileResource, fileHeaders))

        val url = "${whatsAppProperties.graphApiBaseUrl()}/${whatsAppProperties.phoneNumberId}/media"
        return try {
            val response = restTemplate.postForEntity(url, HttpEntity(parts, headers), String::class.java)
            val body = response.body.orEmpty()
            val mediaId = objectMapper.readTree(body).path("id").asText(null)
                ?: throw Exception("Meta no devolvio media id.")
            mediaId
        } catch (ex: HttpStatusCodeException) {
            val metaError = ex.responseBodyAsString.ifBlank { ex.message ?: "Error desconocido de Meta" }
            throw Exception("Meta API ${ex.statusCode.value()}: $metaError")
        }
    }

    fun sendMediaMessage(
        phoneNumber: String,
        kind: WhatsAppOutboundMediaKind,
        mediaId: String,
        caption: String? = null,
        filename: String? = null,
        contextMessageId: String? = null
    ): WhatsAppSendResult {
        if (!whatsAppProperties.isConfigured()) {
            throw Exception("WhatsApp Cloud API no esta configurado correctamente.")
        }
        val body = WhatsAppMediaMessagePayloadBuilder.build(
            phoneNumber = phoneNumber,
            kind = kind,
            mediaId = mediaId,
            caption = caption,
            filename = filename,
            contextMessageId = contextMessageId,
            normalizePhone = ::normalizePhoneNumber,
        )
        return postToMeta(body)
    }

    fun sendTemplateMessageWithMetaResponse(
        phoneNumber: String,
        templateName: String,
        languageCode: String,
        parameters: List<NamedTemplateParameter>,
        callbackToken: String? = null,
        buttonParameter: WhatsAppTemplateButtonParameter? = null
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

        val components = mutableListOf(
            WhatsAppTemplateComponent(
                parameters = parameters.map { param ->
                    WhatsAppTemplateParameter(
                        parameter_name = param.parameterName,
                        text = param.text
                    )
                }
            )
        )
        if (buttonParameter != null) {
            components += WhatsAppTemplateComponent(
                type = "button",
                sub_type = buttonParameter.subType,
                index = buttonParameter.index.toString(),
                parameters = listOf(WhatsAppTemplateParameter(text = buttonParameter.parameter.text))
            )
        }

        val body = WhatsAppTemplateMessageBody(
            to = normalizePhoneNumber(phoneNumber),
            biz_opaque_callback_data = callbackToken,
            template = WhatsAppTemplate(
                name = templateName,
                language = WhatsAppTemplateLanguage(code = languageCode),
                components = components
            )
        )

        return postToMeta(body)
    }

    fun sendTemplateMessage(
        phoneNumber: String,
        templateName: String,
        languageCode: String,
        parameters: List<NamedTemplateParameter>,
        callbackToken: String? = null,
        buttonParameter: WhatsAppTemplateButtonParameter? = null
    ): Boolean {
        return sendTemplateMessageWithMetaResponse(
            phoneNumber = phoneNumber,
            templateName = templateName,
            languageCode = languageCode,
            parameters = parameters,
            callbackToken = callbackToken,
            buttonParameter = buttonParameter
        ).success
    }

    data class InteractiveButtonOption(
        val id: String,
        val title: String
    )

    data class InteractiveListOption(
        val id: String,
        val title: String,
        val description: String? = null
    )

    data class InteractiveListSectionOption(
        val title: String,
        val rows: List<InteractiveListOption>
    )

    fun sendInteractiveReplyButtons(
        phoneNumber: String,
        bodyText: String,
        buttons: List<InteractiveButtonOption>,
        footerText: String? = null,
        contextMessageId: String? = null
    ): WhatsAppSendResult {
        if (!whatsAppProperties.isConfigured()) {
            throw Exception("WhatsApp Cloud API no esta configurado correctamente.")
        }
        WhatsAppInteractiveMessageValidator.validateReplyButtons(
            bodyText = bodyText,
            footerText = footerText,
            buttons = buttons.map { WhatsAppInteractiveOption(it.id, it.title) }
        )
        val normalized = normalizePhoneNumber(phoneNumber)
        val body = WhatsAppInteractiveReplyBody(
            to = normalized,
            interactive = WhatsAppInteractiveContent(
                body = WhatsAppInteractiveText(text = bodyText),
                action = WhatsAppInteractiveAction(
                    buttons = buttons.map { option ->
                        WhatsAppInteractiveActionButton(
                            reply = WhatsAppInteractiveButton(
                                id = option.id.trim(),
                                title = option.title.trim()
                            )
                        )
                    }
                ),
                footer = footerText?.trim()?.takeIf { it.isNotBlank() }?.let { WhatsAppInteractiveText(it) }
            ),
            context = contextMessageId?.trim()?.takeIf { it.isNotBlank() }?.let { WhatsAppMessageContext(it) }
        )
        return postToMeta(body)
    }

    fun sendInteractiveListMessage(
        phoneNumber: String,
        bodyText: String,
        buttonText: String,
        sectionTitle: String,
        rows: List<InteractiveListOption>,
        footerText: String? = null,
        contextMessageId: String? = null
    ): WhatsAppSendResult {
        return sendInteractiveListSectionsMessage(
            phoneNumber = phoneNumber,
            bodyText = bodyText,
            buttonText = buttonText,
            sections = listOf(InteractiveListSectionOption(sectionTitle, rows)),
            footerText = footerText,
            contextMessageId = contextMessageId
        )
    }

    fun sendInteractiveListSectionsMessage(
        phoneNumber: String,
        bodyText: String,
        buttonText: String,
        sections: List<InteractiveListSectionOption>,
        footerText: String? = null,
        contextMessageId: String? = null
    ): WhatsAppSendResult {
        if (!whatsAppProperties.isConfigured()) {
            throw Exception("WhatsApp Cloud API no esta configurado correctamente.")
        }
        WhatsAppInteractiveMessageValidator.validateList(
            bodyText = bodyText,
            buttonText = buttonText,
            footerText = footerText,
            sections = sections.map { section ->
                WhatsAppInteractiveSection(
                    title = section.title,
                    rows = section.rows.map {
                        WhatsAppInteractiveListOption(it.id, it.title, it.description)
                    }
                )
            }
        )
        val normalized = normalizePhoneNumber(phoneNumber)
        val body = WhatsAppInteractiveListReplyBody(
            to = normalized,
            interactive = WhatsAppInteractiveListContent(
                body = WhatsAppInteractiveText(text = bodyText),
                action = WhatsAppInteractiveListAction(
                    button = buttonText.trim(),
                    sections = sections.map { section ->
                        WhatsAppInteractiveListSection(
                            title = section.title.trim(),
                            rows = section.rows.map { option ->
                                WhatsAppInteractiveListRow(
                                    id = option.id.trim(),
                                    title = option.title.trim(),
                                    description = option.description?.trim()?.takeIf { it.isNotBlank() }
                                )
                            }
                        )
                    }
                ),
                footer = footerText?.trim()?.takeIf { it.isNotBlank() }?.let { WhatsAppInteractiveText(it) }
            ),
            context = contextMessageId?.trim()?.takeIf { it.isNotBlank() }?.let { WhatsAppMessageContext(it) }
        )
        return postToMeta(body)
    }

    fun passThreadControl(
        userWaId: String,
        targetAppId: String,
        metadata: String? = null
    ): WhatsAppSendResult {
        if (!whatsAppProperties.isConfigured()) {
            throw Exception("WhatsApp Cloud API no esta configurado correctamente.")
        }
        if (targetAppId.isBlank()) {
            throw IllegalArgumentException("El targetAppId de handoff no esta configurado.")
        }
        val normalized = normalizePhoneNumber(userWaId)
        val body = WhatsAppPassThreadControlBody(
            recipient = WhatsAppThreadControlRecipient(id = normalized),
            target_app_id = targetAppId,
            metadata = metadata?.take(500)
        )
        return postToMetaControl(
            path = "pass_thread_control",
            body = body,
            recipient = normalized
        )
    }

    fun takeThreadControl(
        userWaId: String,
        metadata: String? = null
    ): WhatsAppSendResult {
        if (!whatsAppProperties.isConfigured()) {
            throw Exception("WhatsApp Cloud API no esta configurado correctamente.")
        }
        val normalized = normalizePhoneNumber(userWaId)
        val body = WhatsAppTakeThreadControlBody(
            recipient = WhatsAppThreadControlRecipient(id = normalized),
            metadata = metadata?.take(500)
        )
        return postToMetaControl(
            path = "take_thread_control",
            body = body,
            recipient = normalized
        )
    }

    fun markMessageAsRead(metaMessageId: String): WhatsAppSendResult {
        if (!whatsAppProperties.isConfigured()) {
            throw Exception("WhatsApp Cloud API no esta configurado correctamente.")
        }
        val body = WhatsAppMarkReadBody(message_id = metaMessageId)
        return postToMeta(body)
    }

    fun markMessageAsReadWithTyping(metaMessageId: String): WhatsAppSendResult {
        if (!whatsAppProperties.isConfigured()) {
            throw Exception("WhatsApp Cloud API no esta configurado correctamente.")
        }
        val body = WhatsAppMarkReadBody(
            message_id = metaMessageId,
            typing_indicator = WhatsAppTypingIndicator()
        )
        return postToMeta(body)
    }

    private fun postToMeta(body: Any): WhatsAppSendResult {
        return postToMetaUrl(
            url = whatsAppProperties.messagesUrl(),
            body = body,
            recipient = extractRecipient(body),
            senderPhoneNumberId = whatsAppProperties.phoneNumberId
        )
    }

    private fun postToMetaControl(
        path: String,
        body: Any,
        recipient: String
    ): WhatsAppSendResult {
        return postToMetaUrl(
            url = "${whatsAppProperties.graphApiBaseUrl()}/${whatsAppProperties.phoneNumberId}/$path",
            body = body,
            recipient = recipient,
            senderPhoneNumberId = whatsAppProperties.phoneNumberId
        )
    }

    private fun postToMetaUrl(
        url: String,
        body: Any,
        recipient: String?,
        senderPhoneNumberId: String
    ): WhatsAppSendResult {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(whatsAppProperties.accessToken.trim())

        val request = HttpEntity(body, headers)

        return try {
            val response = restTemplate.postForEntity(
                url,
                request,
                String::class.java
            )
            val responseBody = response.body ?: ""
            log.info("WhatsApp Meta API response: status={} body={}", response.statusCode.value(), responseBody)
            WhatsAppSendResult(
                success = response.statusCode.is2xxSuccessful,
                metaResponse = responseBody,
                metaMessageId = WhatsAppMetaResponseParser.extractMessageId(responseBody),
                recipient = recipient,
                senderPhoneNumberId = senderPhoneNumberId
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
            is WhatsAppInteractiveReplyBody -> body.to
            is WhatsAppInteractiveListReplyBody -> body.to
            is WhatsAppMediaMessageBody -> body.to
            is WhatsAppReactionMessageBody -> body.to
            else -> null
        }
    }

    fun normalizePhoneNumber(phoneNumber: String): String =
        PeruvianWhatsAppPhone.toInternational(phoneNumber)
}
