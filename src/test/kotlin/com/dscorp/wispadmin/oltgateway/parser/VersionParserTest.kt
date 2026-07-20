package com.dscorp.wispadmin.oltgateway.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class VersionParserTest {

    private val parser = VersionParser()

    @Test
    fun `parsea display version`() {
        val output = FixtureLoader.load("display-version.txt")

        val result = parser.parse(output)

        assertEquals("MA5608T", result.product)
        assertEquals("MA5600V800R015C00", result.version)
        assertEquals("SPH106", result.patch)
        assertEquals("120 day(s), 5 hour(s), 32 minute(s), 10 second(s)", result.uptime)
    }
}
