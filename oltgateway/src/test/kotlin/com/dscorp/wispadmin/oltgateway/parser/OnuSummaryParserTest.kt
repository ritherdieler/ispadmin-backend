package com.dscorp.wispadmin.oltgateway.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OnuSummaryParserTest {

    private val parser = OnuSummaryParser()

    @Test
    fun `parsea display ont info summary`() {
        val output = FixtureLoader.load("display-ont-info-summary.txt")

        val result = parser.parse(output)

        assertEquals(3, result.size)
        assertEquals(1, result[0].slot)
        assertEquals(0, result[0].port)
        assertEquals(0, result[0].ontId)
        assertEquals("HWTC11E70E9A", result[0].sn)
        assertEquals("online", result[0].runState)
        assertEquals("offline", result[2].runState)
        assertEquals(1, result[2].port)
    }

    @Test
    fun `parsea display ont info 0 slot all formato live MA5608T`() {
        val output = FixtureLoader.load("display-ont-info-0-slot-all-live.txt")

        val result = parser.parse(output)

        assertEquals(5, result.size)
        assertEquals(0, result[0].frame)
        assertEquals(1, result[0].slot)
        assertEquals(4, result[0].port)
        assertEquals(2, result[0].ontId)
        assertEquals("4857544315F5B806", result[0].sn)
        assertEquals("online", result[0].runState)
        assertEquals("normal", result[0].configState)

        val zteg = result.first { it.ontId == 1 && it.port == 7 }
        assertEquals("5A544547DC47DF15", zteg.sn)
        assertEquals("online", zteg.runState)
        assertEquals(
            "WALTHER MIGUEL NICHO QUISPE_zone_Zone 1_authd_20260716",
            zteg.description
        )

        val withDesc = result.first { it.ontId == 2 && it.port == 4 }
        assertEquals("Some customer name here", withDesc.description)

        val legacy = result.first { it.sn == "HWTC11E70E9A" }
        assertEquals(0, legacy.port)
        assertEquals("success", legacy.configState)
        assertNull(legacy.description)

        assertTrue(result.none { it.sn.contains("WALTHER", ignoreCase = true) })
    }

    @Test
    fun `parsea display ont info 0 all con slots mezclados`() {
        val output = FixtureLoader.load("display-ont-info-0-all-live.txt")

        val result = parser.parse(output)

        assertEquals(6, result.size)
        assertTrue(result.any { it.slot == 0 })
        assertTrue(result.any { it.slot == 1 })
        assertEquals("56534F4C0086F6E9", result.first { it.slot == 0 && it.port == 0 && it.ontId == 0 }.sn)
        assertEquals("4857544315F604E6", result.first { it.slot == 1 && it.port == 0 && it.ontId == 1 }.sn)
        assertEquals(
            "ABRAHAM_MAGENCIO_USURIAGA_zone_Zone_1_authd_20250823",
            result.first { it.sn == "56534F4C0086F6E9" }.description
        )
        assertEquals(
            "JUAN RAMIREZ MUNOZ_zone_Zone 1_descr_Nueve migracion_authd_20251117",
            result.first { it.sn == "56534F4C0086C999" }.description
        )
    }

    @Test
    fun `une description multilinea del mismo comando`() {
        val output = """
            F/S/P   ONT         SN         Control     Run      Config   Match    Protect
                    ID                     flag        state    state    state    side
            ---------------------------------------------------------------------------
            0/ 0/13    11  4857544315F5B806  active      online   normal   match    no
            ---------------------------------------------------------------------------
            F/S/P   ONT-ID   Description
            ---------------------------------------------------------------------------
            0/ 0/13     11   Magda Angelica Pizarro La chira_zone_Zone
                               1_authd_20251202
            MA5608T#
        """.trimIndent()

        val result = parser.parse(output)

        assertEquals(1, result.size)
        assertEquals(
            "Magda Angelica Pizarro La chira_zone_Zone 1_authd_20251202",
            result[0].description
        )
    }
}
