package com.dscorp.wispadmin.oltgateway.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OntLineProfileGemParserTest {

    private val parser = OntLineProfileGemParser()

    @Test
    fun `parsea gem1 vlan 1 y 100 sin 1000`() {
        val output = """
            Profile-ID          :5
            Profile-name        :Generic_1_HF291F96D
               <Gem Index 1>
                Mapping VLAN  Priority Port
                1       1     -        -
                2       100   -        -
        """.trimIndent()

        val mappings = parser.parse(output)

        assertEquals(
            listOf(
                ParsedGemVlanMapping(gem = 1, mapIndex = 1, vlan = 1),
                ParsedGemVlanMapping(gem = 1, mapIndex = 2, vlan = 100),
            ),
            mappings,
        )
    }

    @Test
    fun `parsea gem2 vlan 1000 en profile 12`() {
        val output = """
               <Gem Index 1>
                1       100   -        -
               <Gem Index 2>
                1       1000  -        -
        """.trimIndent()

        val mappings = parser.parse(output)

        assertEquals(1, mappings.single { it.vlan == 100 }.gem)
        assertEquals(2, mappings.single { it.vlan == 1000 }.gem)
        assertEquals(1, mappings.single { it.vlan == 1000 }.mapIndex)
    }
}
