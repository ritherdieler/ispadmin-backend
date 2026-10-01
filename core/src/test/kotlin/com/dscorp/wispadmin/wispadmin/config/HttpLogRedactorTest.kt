package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HttpLogRedactorTest {

    @Test
    fun `redacts sensitive json fields at any depth`() {
        val body = """{"firstName":"Ana","dni":"12345678","phone":"987654321","wifiPassword24":"clave-real",
            |"onu":{"sn":"ZTEG1","pppoePassword":"p1"},"items":[{"accessToken":"abc.def"}],"planId":3}""".trimMargin()

        val redacted = HttpLogRedactor.redactBody(body)

        listOf("12345678", "987654321", "clave-real", "p1", "abc.def").forEach {
            assertFalse(redacted.contains(it), "leaked $it in $redacted")
        }
        assertTrue(redacted.contains("\"planId\":3"))
        assertTrue(redacted.contains("\"sn\":\"ZTEG1\""))
    }

    @Test
    fun `redacts form and query style secrets in non json bodies`() {
        val redacted = HttpLogRedactor.redactBody("user=a&password=hunter2&token=xyz")

        assertFalse(redacted.contains("hunter2"))
        assertFalse(redacted.contains("xyz"))
    }

    @Test
    fun `omits bodies of registration endpoints entirely`() {
        assertEquals(HttpLogRedactor.OMITTED, HttpLogRedactor.bodyForLog("/ispadmin/subscription", """{"dni":"1"}"""))
        assertEquals(
            HttpLogRedactor.OMITTED,
            HttpLogRedactor.bodyForLog("/ispadmin-staging/onu-registration-operations/abc/draft", """{"dni":"1"}"""),
        )
        assertEquals("""{"planId":1}""", HttpLogRedactor.bodyForLog("/ispadmin/plan", """{"planId":1}"""))
    }

    @Test
    fun `authorization header keeps only the scheme`() {
        assertEquals("Bearer ***", HttpLogRedactor.authorization("Bearer eyJhbGciOi.payload.sig"))
        assertEquals("", HttpLogRedactor.authorization(null))
    }
}
