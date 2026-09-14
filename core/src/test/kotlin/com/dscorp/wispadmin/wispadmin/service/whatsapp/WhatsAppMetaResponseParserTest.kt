package com.dscorp.wispadmin.wispadmin.service.whatsapp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WhatsAppMetaResponseParserTest {

    @Test
    fun `extractMessageId returns wamid from messages array`() {
        val response = """
            {"messaging_product":"whatsapp","contacts":[{"input":"51902354183","wa_id":"51902354183"}],
            "messages":[{"id":"wamid.HBgNNTk1MDIzNTQxODMVAgARGBI5QjY5QjY5QjY5QjY5QjY5"}]}
        """.trimIndent()

        assertEquals(
            "wamid.HBgNNTk1MDIzNTQxODMVAgARGBI5QjY5QjY5QjY5QjY5QjY5",
            WhatsAppMetaResponseParser.extractMessageId(response)
        )
    }

    @Test
    fun `extractMessageId returns null for invalid json`() {
        assertNull(WhatsAppMetaResponseParser.extractMessageId("not-json"))
    }

    @Test
    fun `extractMessageId returns null when messages missing`() {
        assertNull(WhatsAppMetaResponseParser.extractMessageId("""{"messaging_product":"whatsapp"}"""))
    }
}
