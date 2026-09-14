package com.dscorp.wispadmin.wispadmin.requestbody

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WhatsAppConversationReplyBodyTest {

    private val mapper = ObjectMapper()

    @Test
    fun `deserializes text field from json object`() {
        val body = mapper.readValue(
            """{"text":"mensaje desde el backoffice de prueba"}""",
            WhatsAppConversationReplyBody::class.java
        )

        assertEquals("mensaje desde el backoffice de prueba", body.text)
    }
}
