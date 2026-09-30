package com.dscorp.wispadmin.oltgateway.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutofindParserTest {

    private val parser = AutofindParser()

    @Test
    fun `parsea display ont autofind all con dos ONTs`() {
        val output = FixtureLoader.load("display-ont-autofind-all.txt")

        val result = parser.parse(output)

        assertEquals(2, result.size)
        assertEquals("4857544311E70E9A", result[0].sn)
        assertEquals(0, result[0].frame)
        assertEquals(0, result[0].slot)
        assertEquals(2, result[0].port)
        assertEquals("HWTC", result[0].vendorId)
        assertEquals("HG8245H", result[0].equipmentId)
        assertEquals("ALCLFCA81B0E", result[1].sn)
        assertEquals(1, result[1].slot)
        assertEquals(0, result[1].port)
        assertEquals("ALCL", result[1].vendorId)
    }

    @Test
    fun `retorna lista vacia cuando no hay autofind`() {
        val result = parser.parse("Number of autofind ONTs: 0\nMA5608T#")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parses VSOL serial in OLT composite format`() {
        val output = """
            MA5608T(config)#display ont autofind all
               -----------------------------------------------------------------------------
               Number              : 1
               F/S/P               : 0/1/6
               Ont SN              : 56534F4C0031C0B6 (VSOL-0031C0B6)
               Password            : [redacted]
               VendorID            : HWTC
               Ont EquipmentID     : VSOLVA74
               Ont autofind time   : 2026-09-30 12:48:22-05:00
               -----------------------------------------------------------------------------
               The number of GPON autofind ONT is 1
            MA5608T(config)#
        """.trimIndent()

        val result = parser.parse(output)

        assertEquals(1, result.size)
        assertEquals("56534F4C0031C0B6 (VSOL-0031C0B6)", result.single().sn)
        assertEquals(1, result.single().slot)
        assertEquals(6, result.single().port)
        assertEquals("VSOLVA74", result.single().equipmentId)
    }
}
