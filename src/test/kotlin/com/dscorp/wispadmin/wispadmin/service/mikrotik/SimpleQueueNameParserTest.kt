package com.dscorp.wispadmin.wispadmin.service.mikrotik

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

    @Test
    fun `parses production owner without env tag`() {
        val owner = SimpleQueueNameParser.owner("id:744, usuario:Judith Quispe, lugar:Huacho")
        assertEquals("", owner?.envTag)
        assertEquals(744, owner?.subscriptionId)
    }

    @Test
    fun `parses staging owner with env tag prefix`() {
        val owner = SimpleQueueNameParser.owner("[stg] id:2291, usuario:Sergio Carrillo, lugar:Huacho")
        assertEquals("stg", owner?.envTag)
        assertEquals(2291, owner?.subscriptionId)
        assertEquals(2291, SimpleQueueNameParser.subscriptionId("[stg] id:2291, usuario:Sergio Carrillo"))
    }

    @Test
    fun `same subscription id in another env is not the same owner`() {
        val prod = SimpleQueueNameParser.owner("id:2291, usuario:Sergio")
        val staging = SimpleQueueNameParser.owner("[stg] id:2291, usuario:Sergio")
        assertEquals(2291, prod?.subscriptionId)
        assertEquals(2291, staging?.subscriptionId)
        assertEquals("", prod?.envTag)
        assertEquals("stg", staging?.envTag)
        assertFalse(prod == staging)
    }

    @Test
    fun `owner is null for orphan and blank names`() {
        assertNull(SimpleQueueNameParser.owner("Judith Quispe - f50"))
        assertNull(SimpleQueueNameParser.owner(null))
        assertNull(SimpleQueueNameParser.owner("  "))
    }
}
