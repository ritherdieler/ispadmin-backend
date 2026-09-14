package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WhatsAppTemplateSyncServiceBodyTextTest {

    private val objectMapper = ObjectMapper()

    @Test
    fun `extractBodyText reads BODY component from Meta template node`() {
        val node = objectMapper.readTree(
            """
            {
              "id": "123",
              "name": "payment_reminder_gigaperu",
              "status": "APPROVED",
              "language": "es_PE",
              "components": [
                {"type": "HEADER", "format": "TEXT", "text": "GigaFiber"},
                {
                  "type": "BODY",
                  "text": "Estimado(a) {{customer_name}}, recuerde pagar {{amount}} antes del {{billing_period}}."
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(
            "Estimado(a) {{customer_name}}, recuerde pagar {{amount}} antes del {{billing_period}}.",
            WhatsAppTemplateSyncService.extractBodyText(node)
        )
    }

    @Test
    fun `extractBodyText returns null when BODY missing`() {
        val node = objectMapper.readTree(
            """{"id":"1","name":"x","components":[{"type":"HEADER","text":"Hi"}]}"""
        )

        assertEquals(null, WhatsAppTemplateSyncService.extractBodyText(node))
    }
}
