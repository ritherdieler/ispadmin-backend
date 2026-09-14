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

    @Test
    fun `parse audio with media id and ogg mime`() {
        val node = mapper.readTree("""
            {
              "id":"wamid.5","from":"51902354183","type":"audio",
              "audio":{"id":"media-audio-1","mime_type":"audio/ogg; codecs=opus","voice":true}
            }
        """.trimIndent())
        val payload = WhatsAppInboundPayloadParser.parse(node)!!
        assertEquals("audio", payload.messageType)
        assertEquals("media-audio-1", payload.mediaId)
        assertEquals("audio/ogg; codecs=opus", payload.mediaMimeType)
        assertEquals(null, payload.messageText)
    }

    @Test
    fun `parse sticker as image-like media`() {
        val node = mapper.readTree("""
            {
              "id":"wamid.6","from":"51902354183","type":"sticker",
              "sticker":{"id":"media-sticker-1","mime_type":"image/webp","animated":false}
            }
        """.trimIndent())
        val payload = WhatsAppInboundPayloadParser.parse(node)!!
        assertEquals("sticker", payload.messageType)
        assertEquals("media-sticker-1", payload.mediaId)
        assertEquals("image/webp", payload.mediaMimeType)
    }

    @Test
    fun `parse reaction extracts target wamid and emoji`() {
        val node = mapper.readTree("""
            {
              "id":"wamid.reaction.1","from":"51902354183","type":"reaction",
              "reaction":{"message_id":"wamid.OUTBOUND.99","emoji":"❤️"}
            }
        """.trimIndent())
        val payload = WhatsAppInboundPayloadParser.parse(node)!!
        assertEquals("reaction", payload.messageType)
        assertEquals("wamid.OUTBOUND.99", payload.reactionMessageId)
        assertEquals("❤️", payload.reactionEmoji)
    }
}
