package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class WhatsAppInboundPayloadParserTest {

    private val mapper = ObjectMapper()

    @Test
    fun `parse text message`() {
        val node = mapper.readTree("""
            {"id":"wamid.1","from":"51902354183","type":"text","text":{"body":"Hola"}}
        """.trimIndent())
        val payload = WhatsAppInboundPayloadParser.parse(node)!!
        assertEquals("text", payload.messageType)
        assertEquals("Hola", payload.messageText)
    }

    @Test
    fun `parse button_reply from interactive`() {
        val node = mapper.readTree("""
            {
              "id":"wamid.2","from":"51902354183","type":"interactive",
              "context":{"id":"wamid.outbound"},
              "interactive":{"type":"button_reply","button_reply":{"id":"ver_deuda","title":"Ver deuda"}}
            }
        """.trimIndent())
        val payload = WhatsAppInboundPayloadParser.parse(node)!!
        assertEquals("button_reply", payload.messageType)
        assertEquals("ver_deuda", payload.buttonReplyId)
        assertEquals("Ver deuda", payload.buttonReplyTitle)
        assertEquals("wamid.outbound", payload.contextMessageId)
    }

    @Test
    fun `parse button click from template quick reply`() {
        val node = mapper.readTree("""
            {
              "id":"wamid.4","from":"51902354183","type":"button",
              "context":{"id":"wamid.outbound-template"},
              "button":{"payload":"pagar_ahora","text":"Pagar ahora"}
            }
        """.trimIndent())
        val payload = WhatsAppInboundPayloadParser.parse(node)!!
        assertEquals("button_reply", payload.messageType)
        assertEquals("pagar_ahora", payload.buttonReplyId)
        assertEquals("Pagar ahora", payload.buttonReplyTitle)
        assertEquals("wamid.outbound-template", payload.contextMessageId)
    }

    @Test
    fun `parse image with media id`() {
        val node = mapper.readTree("""
            {
              "id":"wamid.3","from":"51902354183","type":"image",
              "image":{"id":"media-99","mime_type":"image/jpeg","caption":"comprobante"}
            }
        """.trimIndent())
        val payload = WhatsAppInboundPayloadParser.parse(node)!!
        assertEquals("image", payload.messageType)
        assertEquals("media-99", payload.mediaId)
        assertNotNull(payload.mediaMimeType)
    }
}
