package com.dscorp.wispadmin.wispadmin.service.mikrotik

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SimpleQueueNameParserTest {

    @Test
    fun `parses subscription id from queue name`() {
        assertEquals(
            744,
            SimpleQueueNameParser.subscriptionId("id:744, usuario:Judith Quispe, lugar:Huacho")
        )
    }

    @Test
    fun `returns null for orphan legacy name`() {
        assertNull(SimpleQueueNameParser.subscriptionId("Judith Quispe - f50"))
        assertNull(SimpleQueueNameParser.subscriptionId(null))
        assertNull(SimpleQueueNameParser.subscriptionId("  "))
    }
}
