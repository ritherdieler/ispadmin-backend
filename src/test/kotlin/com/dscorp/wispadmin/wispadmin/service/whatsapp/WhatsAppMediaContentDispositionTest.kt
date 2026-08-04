package com.dscorp.wispadmin.wispadmin.service.whatsapp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WhatsAppMediaContentDispositionTest {

    @Test
    fun `image and audio use inline disposition`() {
        assertTrue(WhatsAppMediaContentDisposition.forMimeType("image/jpeg", "a.jpg").startsWith("inline;"))
        assertTrue(WhatsAppMediaContentDisposition.forMimeType("audio/ogg", "v.ogg").startsWith("inline;"))
    }

    @Test
    fun `documents use attachment disposition`() {
        assertEquals(
            "attachment; filename=\"doc.pdf\"",
            WhatsAppMediaContentDisposition.forMimeType("application/pdf", "doc.pdf")
        )
        assertTrue(WhatsAppMediaContentDisposition.forMimeType(null, "file.bin").startsWith("attachment;"))
    }
}
