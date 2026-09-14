package com.dscorp.wispadmin.oltgateway.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoardParserTest {

    private val parser = BoardParser()

    @Test
    fun `parsea display board 0`() {
        val output = FixtureLoader.load("display-board-0.txt")

        val result = parser.parse(output, expectedSlot = 0)

        assertEquals(0, result.slot)
        assertEquals("H805GPFD", result.boardName)
        assertEquals("Normal", result.status)
    }

    @Test
    fun `parsea display board 1`() {
        val output = FixtureLoader.load("display-board-1.txt")

        val result = parser.parse(output, expectedSlot = 1)

        assertEquals(1, result.slot)
        assertEquals("H806GPFD", result.boardName)
        assertEquals("Normal", result.status)
    }

    @Test
    fun `parseAll lee tabla chassis MA5608T display board frame 0`() {
        val output = FixtureLoader.load("display-board-frame-0-chassis.txt")

        val boards = parser.parseAll(output)

        assertEquals(
            listOf(
                ParsedBoard(0, "H805GPFD", "Normal"),
                ParsedBoard(1, "H806GPFD", "Normal"),
                ParsedBoard(3, "H801MCUD1", "Active_normal"),
                ParsedBoard(4, "H801MPWD", "Normal")
            ),
            boards
        )
    }

    @Test
    fun `parseAll ignora salida parameter error`() {
        val output = "display board 1\n                      ^\n  % Parameter error, the error locates at '^'\nMA5608T>"

        assertTrue(parser.parseAll(output).isEmpty())
    }
}
