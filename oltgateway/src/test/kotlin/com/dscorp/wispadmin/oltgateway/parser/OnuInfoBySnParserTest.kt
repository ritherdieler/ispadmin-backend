package com.dscorp.wispadmin.oltgateway.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OnuInfoBySnParserTest {

    private val parser = OnuInfoBySnParser()

    @Test
    fun `parsea display ont info by-sn`() {
        val output = FixtureLoader.load("display-ont-info-by-sn.txt")

        val result = parser.parse(output)

        assertEquals("4857544311E70E9A", result?.sn)
        assertEquals(0, result?.frame)
        assertEquals(1, result?.slot)
        assertEquals(0, result?.port)
        assertEquals(5, result?.ontId)
        assertEquals("cliente_demo", result?.description)
        assertEquals("online", result?.runState)
        assertEquals("active", result?.controlFlag)
        assertEquals("line-profile_10", result?.lineProfileName)
        assertEquals(10, result?.lineProfileId)
    }

    @Test
    fun `parsea formato live FSP y SN con vendor`() {
        val output = FixtureLoader.load("display-ont-info-by-sn-live.txt")

        val result = parser.parse(output)

        assertEquals("ZTEGDC47DF15", result?.sn)
        assertEquals(0, result?.frame)
        assertEquals(1, result?.slot)
        assertEquals(7, result?.port)
        assertEquals(1, result?.ontId)
        assertEquals("online", result?.runState)
        assertEquals("active", result?.controlFlag)
        assertEquals("Generic_1_V1", result?.lineProfileName)
        assertEquals(3, result?.lineProfileId)
        assertEquals("Generic_1_V1", result?.serviceProfileName)
        assertEquals(2, result?.serviceProfileId)
    }

    @Test
    fun `retorna null cuando no encuentra ONU`() {
        val result = parser.parse("Failure: The ONT does not exist\nMA5608T#")
        assertNull(result)
    }
}
