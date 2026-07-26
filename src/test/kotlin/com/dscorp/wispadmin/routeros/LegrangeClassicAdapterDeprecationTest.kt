package com.dscorp.wispadmin.routeros

import com.dscorp.wispadmin.routeros.adapter.LegrangeClassicAdapter
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LegrangeClassicAdapterDeprecationTest {

    @Test
    fun `LegrangeClassicAdapter is marked deprecated with migration note`() {
        val deprecated = LegrangeClassicAdapter::class.java.getAnnotation(Deprecated::class.java)
        assertTrue(deprecated != null)
        assertTrue(deprecated!!.message.contains("adapter=rest"))
        assertTrue(deprecated.message.contains("R5"))
    }
}
