package com.dscorp.wispadmin.wispadmin.requestbody

import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppMediaMessagePayloadBuilder
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppOutboundMediaKind
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WhatsAppMediaMessagePayloadBuilderTest {

    private val mapper = ObjectMapper()

    @Test
    fun `Test 1 audio JSON must not contain caption substring`() {
        val payload = WhatsAppMediaMessagePayloadBuilder.build(
            phoneNumber = "51987654321",
            kind = WhatsAppOutboundMediaKind.AUDIO,
            mediaId = "media-audio-1",
            caption = "esta caption no debe ir a Meta",
            filename = "voice-note.ogg",
        )

        val json = mapper.writeValueAsString(payload)
        assertTrue(json.contains("\"type\":\"audio\""))
        assertTrue(json.contains("\"id\":\"media-audio-1\""))
        assertTrue(json.contains("\"voice\":true"))
        assertFalse(json.contains("caption"), "Meta Error 100 rejects caption on audio: $json")
    }

    @Test
    fun `Test 2 image JSON must contain caption when provided`() {
        val payload = WhatsAppMediaMessagePayloadBuilder.build(
            phoneNumber = "51987654321",
            kind = WhatsAppOutboundMediaKind.IMAGE,
            mediaId = "media-image-1",
            caption = "Voucher de pago",
        )

        val json = mapper.writeValueAsString(payload)
        assertTrue(json.contains("\"type\":\"image\""))
        assertTrue(json.contains("\"caption\":\"Voucher de pago\""))
    }

    @Test
    fun `document payload includes caption when present`() {
        val body = WhatsAppMediaMessagePayloadBuilder.build(
            phoneNumber = "51987654321",
            kind = WhatsAppOutboundMediaKind.DOCUMENT,
            mediaId = "media-doc-1",
            caption = "Contrato PDF",
            filename = "contrato.pdf",
            contextMessageId = null,
        )

        val json = mapper.writeValueAsString(body)
        assertTrue(json.contains("\"type\":\"document\""))
        assertTrue(json.contains("\"caption\":\"Contrato PDF\""))
        assertTrue(json.contains("\"filename\":\"contrato.pdf\""))
    }

    @Test
    fun `audio payload omits caption key when caption is absent`() {
        val body = WhatsAppMediaMessagePayloadBuilder.build(
            phoneNumber = "987654321",
            kind = WhatsAppOutboundMediaKind.AUDIO,
            mediaId = "media-audio-2",
            caption = null,
            filename = "clip.ogg",
        )

        val json = mapper.writeValueAsString(body)
        assertFalse(json.contains("caption"), "null caption must not be serialized: $json")
        assertFalse(json.contains("\"voice\""), "regular audio file should omit voice unless voice-note")
    }
}
