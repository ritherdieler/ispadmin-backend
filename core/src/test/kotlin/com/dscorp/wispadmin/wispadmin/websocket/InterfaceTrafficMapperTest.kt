package com.dscorp.wispadmin.wispadmin.websocket

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InterfaceTrafficMapperTest {

    @Test
    fun `maps pppoe-in with byte counters`() {
        val row = InterfaceTrafficMapper.toWsRow(
            mapOf(
                "name" to "<pppoe-gf6>",
                "type" to "pppoe-in",
                "rx-byte" to "2147483648",
                "tx-byte" to "152043520",
                "rx-packet" to "10",
                "tx-packet" to "4",
                "running" to "true",
                "disabled" to "false",
            )
        )

        assertEquals("<pppoe-gf6>", row["name"])
        assertEquals("pppoe-in", row["type"])
        assertEquals("2147483648", row["rxBytes"])
        assertEquals("152043520", row["txBytes"])
    }
}
