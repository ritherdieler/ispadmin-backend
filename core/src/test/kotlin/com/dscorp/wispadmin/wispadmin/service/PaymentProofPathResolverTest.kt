package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class PaymentProofPathResolverTest {

    private val basePath = "/usr/local/tomcat/data/whatsapp/media"
    private val resolver = PaymentProofPathResolver(
        WhatsAppProperties().apply { media.basePath = basePath },
    )

    @Test
    fun `toStoredFilename keeps only filename with extension`() {
        val stored = resolver.toStoredFilename(
            "/usr/local/tomcat/./data/whatsapp/media/4481006472154622_49c54a24-f98b-4cc3-9234-ead95d404c10.jpg",
        )
        assertEquals("4481006472154622_49c54a24-f98b-4cc3-9234-ead95d404c10.jpg", stored)
    }

    @Test
    fun `toPublicPath concatenates configured base path with filename`() {
        val publicPath = resolver.toPublicPath("receipt-001.jpg")
        assertEquals(Paths.get(basePath, "receipt-001.jpg").normalize().toString(), publicPath)
    }

    @Test
    fun `toPublicPath extracts filename from legacy absolute path before concatenating`() {
        val publicPath = resolver.toPublicPath("/old/volume/proof.jpg")
        assertEquals(Paths.get(basePath, "proof.jpg").normalize().toString(), publicPath)
    }

    @Test
    fun `blank paths stay null`() {
        assertNull(resolver.toStoredFilename(null))
        assertNull(resolver.toStoredFilename("  "))
        assertNull(resolver.toPublicPath(null))
    }
}
