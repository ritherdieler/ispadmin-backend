package com.dscorp.wispadmin.wispadmin.requestbody

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WhatsAppSendBodiesCallbackDataTest {

    private val mapper = ObjectMapper()

    @Test
    fun `template body omits biz_opaque_callback_data when null`() {
        val body = WhatsAppTemplateMessageBody(
            to = "51987654321",
            template = WhatsAppTemplate(
                name = "payment_reminder_gigaperu",
                language = WhatsAppTemplateLanguage(code = "es_PE"),
                components = listOf(WhatsAppTemplateComponent(parameters = emptyList()))
            )
        )

        val json = mapper.writeValueAsString(body)

        assertFalse(json.contains("biz_opaque_callback_data"))
    }

    @Test
    fun `template body includes biz_opaque_callback_data when present`() {
        val body = WhatsAppTemplateMessageBody(
            to = "51987654321",
            template = WhatsAppTemplate(
                name = "payment_reminder_gigaperu",
                language = WhatsAppTemplateLanguage(code = "es_PE"),
                components = listOf(WhatsAppTemplateComponent(parameters = emptyList()))
            ),
            biz_opaque_callback_data = "cb-123"
        )

        val json = mapper.writeValueAsString(body)

        assertTrue(json.contains("\"biz_opaque_callback_data\":\"cb-123\""))
    }

    @Test
    fun `text body omits biz_opaque_callback_data when null`() {
        val body = WhatsAppTextMessageBody(
            to = "51987654321",
            text = WhatsAppTextContent(body = "Hola")
        )

        val json = mapper.writeValueAsString(body)

        assertFalse(json.contains("biz_opaque_callback_data"))
    }

    @Test
    fun `media body omits biz_opaque_callback_data and null media fields`() {
        val body = WhatsAppMediaMessageBody(
            to = "51987654321",
            type = "image",
            image = WhatsAppMediaIdPayload(id = "media-1")
        )

        val json = mapper.writeValueAsString(body)

        assertFalse(json.contains("biz_opaque_callback_data"))
        assertFalse(json.contains("\"document\":null"))
        assertFalse(json.contains("\"audio\":null"))
    }
}
