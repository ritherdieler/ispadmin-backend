package com.dscorp.wispadmin.wispadmin.service.genieacs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Tr069SerialMatcherTest {

    @Test
    fun `normalizeSuffix takes last 6 hex chars uppercase`() {
        assertEquals("31C0B6", Tr069SerialMatcher.normalizeSuffix("VSOL0031C0B6"))
        assertEquals("31C0B6", Tr069SerialMatcher.normalizeSuffix("12345B4641531C0B6"))
        assertEquals("31C0B6", Tr069SerialMatcher.normalizeSuffix("vsol0031c0b6"))
    }

    @Test
    fun `normalizeSuffix returns null for blank or short serial`() {
        assertNull(Tr069SerialMatcher.normalizeSuffix(null))
        assertNull(Tr069SerialMatcher.normalizeSuffix("  "))
        assertNull(Tr069SerialMatcher.normalizeSuffix("ABC"))
        assertNull(Tr069SerialMatcher.normalizeSuffix("XYZ12G"))
    }

    @Test
    fun `findUnique returns Found when exactly one device matches suffix`() {
        val devices = listOf(
            device("B46415-V2804AX15T-12345B4641531C0B6", "12345B4641531C0B6"),
            device("OTHER-MODEL-AAAABBBBCCCC", "AAAABBBBCCCC"),
        )

        val result = Tr069SerialMatcher.findUnique("VSOL0031C0B6", devices)

        assertTrue(result is Tr069SerialMatch.Found)
        assertEquals(
            "B46415-V2804AX15T-12345B4641531C0B6",
            (result as Tr069SerialMatch.Found).device.id
        )
    }

    @Test
    fun `findUnique returns None when no device matches`() {
        val devices = listOf(device("OTHER-MODEL-AAAABBBBCCCC", "AAAABBBBCCCC"))

        val result = Tr069SerialMatcher.findUnique("VSOL0031C0B6", devices)

        assertTrue(result is Tr069SerialMatch.None)
        assertEquals("31C0B6", (result as Tr069SerialMatch.None).suffix)
    }

    @Test
    fun `findUnique returns Ambiguous when more than one device matches`() {
        val devices = listOf(
            device("DEV-1-XXXXXX31C0B6", "XXXXXX31C0B6"),
            device("DEV-2-YYYYYY31C0B6", "YYYYYY31C0B6"),
        )

        val result = Tr069SerialMatcher.findUnique("VSOL0031C0B6", devices)

        assertTrue(result is Tr069SerialMatch.Ambiguous)
        assertEquals(2, (result as Tr069SerialMatch.Ambiguous).count)
    }

    @Test
    fun `findUnique returns InvalidSerial for unusable SN`() {
        val result = Tr069SerialMatcher.findUnique("!!", emptyList())
        assertTrue(result is Tr069SerialMatch.InvalidSerial)
    }

    @Test
    fun `findUnique maps SmartOLT HWTC serial to ACS hex serial`() {
        val devices = listOf(
            device("00259E-HG8145X6-48575443C6FBA6AA", "48575443C6FBA6AA"),
        )

        val result = Tr069SerialMatcher.findUnique("HWTCC6FBA6AA", devices)

        assertTrue(result is Tr069SerialMatch.Found)
        assertEquals(
            "00259E-HG8145X6-48575443C6FBA6AA",
            (result as Tr069SerialMatch.Found).device.id,
        )
        assertEquals("FBA6AA", Tr069SerialMatcher.normalizeSuffix("HWTCC6FBA6AA"))
        assertEquals("FBA6AA", Tr069SerialMatcher.normalizeSuffix("48575443C6FBA6AA"))
    }

    private fun device(id: String, serial: String) = GenieAcsDevice(
        id = id,
        serialNumber = serial,
        productClass = "V2804AX15T",
        lastInform = null,
    )
}
