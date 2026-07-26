package com.dscorp.wispadmin.netdiag.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RouterOsUptimeParserTest {

    @Test
    fun `parsea uptime RouterOS`() {
        assertEquals(93784L, RouterOsUptimeParser.parseSeconds("1d2h3m4s"))
        assertEquals(604800L, RouterOsUptimeParser.parseSeconds("1w"))
        assertEquals(45L, RouterOsUptimeParser.parseSeconds("45s"))
    }

    @Test
    fun `null o vacio retorna null`() {
        assertNull(RouterOsUptimeParser.parseSeconds(null))
        assertNull(RouterOsUptimeParser.parseSeconds(""))
        assertNull(RouterOsUptimeParser.parseSeconds("n/a"))
    }
}
