package com.dscorp.wispadmin.wispadmin.requestbody

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WhatsAppInteractiveMessageBodyTest {

    private val mapper = ObjectMapper()

    @Test
    fun `mark read omits typing when it is not requested`() {
        val json = mapper.readTree(mapper.writeValueAsString(WhatsAppMarkReadBody(message_id = "wamid.1")))

        assertEquals("read", json.path("status").asText())
        assertFalse(json.has("typing_indicator"))
    }

    @Test
    fun `mark read includes text typing indicator`() {
        val json = mapper.readTree(
            mapper.writeValueAsString(
                WhatsAppMarkReadBody(
                    message_id = "wamid.1",
                    typing_indicator = WhatsAppTypingIndicator()
                )
            )
        )

        assertEquals("text", json.path("typing_indicator").path("type").asText())
    }

    @Test
    fun `interactive reply serializes footer and contextual message`() {
        val body = WhatsAppInteractiveReplyBody(
            to = "51999999999",
            interactive = WhatsAppInteractiveContent(
                body = WhatsAppInteractiveText("Selecciona una opción"),
                action = WhatsAppInteractiveAction(
                    buttons = listOf(
                        WhatsAppInteractiveActionButton(
                            reply = WhatsAppInteractiveButton("main", "Menú principal")
                        )
                    )
                ),
                footer = WhatsAppInteractiveText("Escribe MENÚ para volver")
            ),
            context = WhatsAppMessageContext("wamid.inbound")
        )

        val json = mapper.readTree(mapper.writeValueAsString(body))

        assertEquals("Escribe MENÚ para volver", json.path("interactive").path("footer").path("text").asText())
        assertEquals("wamid.inbound", json.path("context").path("message_id").asText())
        assertTrue(json.path("interactive").path("action").path("buttons").isArray)
    }
}
