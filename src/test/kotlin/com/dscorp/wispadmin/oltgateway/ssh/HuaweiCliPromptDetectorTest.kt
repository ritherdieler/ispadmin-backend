package com.dscorp.wispadmin.oltgateway.ssh

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HuaweiCliPromptDetectorTest {

    @Test
    fun `completo con prompt de modo config`() {
        assertTrue(HuaweiCliPromptDetector.isComplete("MA5608T(config)#\n"))
        assertTrue(HuaweiCliPromptDetector.isComplete("MA5608T(config-if-gpon-0/1)#\n"))
    }

    @Test
    fun `completo cuando el prompt esta al final sin More`() {
        val buffer = """
            0/ 1/4    2  4857544315F5B806  active      online   normal   match    no
            MA5608T#
        """.trimIndent()

        assertTrue(HuaweiCliPromptDetector.isComplete(buffer))
        assertFalse(HuaweiCliPromptDetector.needsMorePage(buffer))
    }

    @Test
    fun `no completo si hay More en la cola aunque el buffer tenga prompts viejos`() {
        val buffer = """
            MA5608T#display ont info 0 1 all
            0/ 1/4    2  4857544315F5B806  active      online   normal   match    no
            ---- More ( Press 'Q' to break ) ----
        """.trimIndent()

        assertFalse(HuaweiCliPromptDetector.isComplete(buffer))
        assertTrue(HuaweiCliPromptDetector.needsMorePage(buffer))
    }

    @Test
    fun `completo si More ya no esta en la cola aunque quede More viejo arriba`() {
        val buffer = buildString {
            append("page1\n---- More ( Press 'Q' to break ) ----\n")
            append("row\n".repeat(80))
            append("0/ 1/7    1  5A544547DC47DF15  active      online   normal   match    no\n")
            append("MA5608T#\n")
        }

        assertTrue(HuaweiCliPromptDetector.isComplete(buffer))
        assertFalse(HuaweiCliPromptDetector.needsMorePage(buffer))
    }
}
