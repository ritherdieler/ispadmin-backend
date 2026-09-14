package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppInboundMessage
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WhatsAppPaymentProofDetectorTest {

    @Test
    fun `image is payment proof`() {
        assertTrue(
            WhatsAppThreadMessageMapper.inboundIsPaymentProof(
                inbound(messageType = "image", mediaMimeType = "image/jpeg")
            )
        )
    }

    @Test
    fun `pdf document is payment proof`() {
        assertTrue(
            WhatsAppThreadMessageMapper.inboundIsPaymentProof(
                inbound(messageType = "document", mediaMimeType = "application/pdf")
            )
        )
        assertTrue(
            WhatsAppThreadMessageMapper.inboundIsPaymentProof(
                inbound(messageType = "document", mediaMimeType = "application/pdf; charset=binary")
            )
        )
    }

    @Test
    fun `audio sticker and non pdf documents are not payment proof`() {
        assertFalse(
            WhatsAppThreadMessageMapper.inboundIsPaymentProof(
                inbound(messageType = "audio", mediaMimeType = "audio/ogg", mediaId = "m1")
            )
        )
        assertFalse(
            WhatsAppThreadMessageMapper.inboundIsPaymentProof(
                inbound(messageType = "sticker", mediaMimeType = "image/webp", mediaId = "m2")
            )
        )
        assertFalse(
            WhatsAppThreadMessageMapper.inboundIsPaymentProof(
                inbound(messageType = "document", mediaMimeType = "application/msword", mediaId = "m3")
            )
        )
        assertFalse(
            WhatsAppThreadMessageMapper.inboundIsPaymentProof(
                inbound(messageType = "video", mediaMimeType = "video/mp4", mediaId = "m4")
            )
        )
    }

    @Test
    fun `inboundHasMedia still true for audio`() {
        assertTrue(
            WhatsAppThreadMessageMapper.inboundHasMedia(
                inbound(messageType = "audio", mediaId = "m1", mediaMimeType = "audio/ogg")
            )
        )
        assertFalse(
            WhatsAppThreadMessageMapper.inboundIsPaymentProof(
                inbound(messageType = "audio", mediaId = "m1", mediaMimeType = "audio/ogg")
            )
        )
    }

    private fun inbound(
        messageType: String,
        mediaMimeType: String? = null,
        mediaId: String? = "media-1"
    ) = WhatsAppInboundMessage(
        metaMessageId = "wamid.test",
        phone = "51999999999",
        messageType = messageType,
        mediaMimeType = mediaMimeType,
        mediaId = mediaId
    )
}
