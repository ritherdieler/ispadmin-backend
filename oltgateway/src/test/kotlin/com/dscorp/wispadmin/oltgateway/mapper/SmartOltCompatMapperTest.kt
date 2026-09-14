package com.dscorp.wispadmin.oltgateway.mapper

import com.dscorp.wispadmin.oltgateway.parser.ParsedAutofindOnt
import com.dscorp.wispadmin.oltgateway.parser.ParsedOnuBySn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmartOltCompatMapperTest {

    private val mapper = SmartOltCompatMapper()

    @Test
    fun `mapea autofind a UnconfirmedOnuResponse SmartOLT`() {
        val items = listOf(
            ParsedAutofindOnt(
                sn = "4857544311E70E9A",
                frame = 0,
                slot = 0,
                port = 2,
                equipmentId = "HG8245H"
            )
        )

        val result = mapper.toUnconfirmedOnuResponse(items, "gigafiber-ma5608t")

        assertTrue(result.status)
        assertEquals(1, result.response.size)
        assertEquals("4857544311E70E9A", result.response[0].sn)
        assertEquals("0", result.response[0].board)
        assertEquals("2", result.response[0].port)
        assertEquals("gpon", result.response[0].pon_type)
        assertEquals("gigafiber-ma5608t", result.response[0].olt_id)
        assertEquals("HG8245H", result.response[0].onu_type_name)
    }

    @Test
    fun `mapea by-sn a OnuBySnResponse SmartOLT`() {
        val parsed = ParsedOnuBySn(
            sn = "4857544311E70E9A",
            frame = 0,
            slot = 1,
            port = 0,
            ontId = 5,
            description = "cliente_demo",
            runState = "online",
            lineProfileName = "line-profile_10"
        )

        val result = mapper.toOnuBySnResponse(parsed, "gigafiber-ma5608t")

        assertTrue(result.status)
        assertEquals("200", result.response_code)
        assertEquals(1, result.onus.size)
        val onu = result.onus[0]
        assertEquals("4857544311E70E9A", onu.sn)
        assertEquals("1", onu.board)
        assertEquals("0", onu.port)
        assertEquals("5", onu.onu)
        assertEquals("cliente_demo", onu.name)
        assertEquals("online", onu.administrative_status)
        assertEquals("line-profile_10", onu.custom_template_name)
        assertEquals("gigafiber-ma5608t_1_0_5", onu.unique_external_id)
    }

    @Test
    fun `by-sn null retorna 404`() {
        val result = mapper.toOnuBySnResponse(null, "gigafiber-ma5608t")
        assertFalse(result.status)
        assertEquals("404", result.response_code)
        assertTrue(result.onus.isEmpty())
    }
}
