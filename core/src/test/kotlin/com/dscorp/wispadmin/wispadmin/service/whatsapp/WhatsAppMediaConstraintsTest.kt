package com.dscorp.wispadmin.wispadmin.service.whatsapp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WhatsAppMediaConstraintsTest {

    @Test
    fun `accepts image jpeg under 5MB`() {
        val kind = WhatsAppMediaConstraints.validate(
            mimeType = "image/jpeg",
            sizeBytes = 4_000_000,
            filename = "voucher.jpg"
        )
        assertEquals(WhatsAppOutboundMediaKind.IMAGE, kind)
    }

    @Test
    fun `rejects image over 5MB`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            WhatsAppMediaConstraints.validate(
                mimeType = "image/png",
                sizeBytes = WhatsAppMediaConstraints.MAX_IMAGE_BYTES + 1,
                filename = "big.png"
            )
        }
        assertTrue(ex.message!!.contains("5"))
    }

    @Test
    fun `accepts pdf document under 100MB`() {
        val kind = WhatsAppMediaConstraints.validate(
            mimeType = "application/pdf",
            sizeBytes = 10_000_000,
            filename = "contrato.pdf"
        )
        assertEquals(WhatsAppOutboundMediaKind.DOCUMENT, kind)
    }

    @Test
    fun `accepts audio ogg under 16MB`() {
        val kind = WhatsAppMediaConstraints.validate(
            mimeType = "audio/ogg",
            sizeBytes = 1_000_000,
            filename = "nota.ogg"
        )
        assertEquals(WhatsAppOutboundMediaKind.AUDIO, kind)
    }

    @Test
    fun `rejects unsupported mime including sticker webp`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            WhatsAppMediaConstraints.validate(
                mimeType = "image/webp",
                sizeBytes = 50_000,
                filename = "sticker.webp"
            )
        }
        assertTrue(ex.message!!.contains("no soportado", ignoreCase = true))
    }

    @Test
    fun `rejects empty payload`() {
        assertThrows(IllegalArgumentException::class.java) {
            WhatsAppMediaConstraints.validate(
                mimeType = "image/jpeg",
                sizeBytes = 0,
                filename = "empty.jpg"
            )
        }
    }
}
