package com.dscorp.wispadmin.oltgateway.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HuaweiOltAlarmParserFallbackTest {

    private val parser = HuaweiOltAlarmParser()

    @Test
    fun `bloque sin ALARM NAME se guarda como OLT_ALARM_UNPARSED`() {
        val raw = """
          ALARM 9 FAULT MAJOR 0xabcdef01 EQUIPMENT 2026-07-31 12:00:00-05:00
          SRVEFF      : SA
          PARAMETERS  : FrameID: 0, SlotID: 0, PortID: 1
          garbo garbage without name field
          --- END
        """.trimIndent()

        val alarms = parser.parseActiveAlarms(raw)

        assertEquals(1, alarms.size)
        assertEquals("OLT_ALARM_UNPARSED", alarms[0].reasonCode)
        assertEquals("UNPARSED", alarms[0].alarmName)
        assertEquals("P2", alarms[0].severity)
        assertFalse(alarms[0].isClear)
        assertTrue(alarms[0].rawBlock.contains("0xabcdef01"))
        assertTrue(alarms[0].rawBlock.contains("garbo garbage"))
    }

    @Test
    fun `raw sin marcadores END se guarda completo como unparsed`() {
        val raw = "weird olt dump without structured alarms %%ddALARM/1 something"

        val alarms = parser.parseActiveAlarms(raw)

        assertEquals(1, alarms.size)
        assertEquals("OLT_ALARM_UNPARSED", alarms[0].reasonCode)
        assertEquals(raw, alarms[0].rawBlock)
    }

    @Test
    fun `mezcla parseable y no parseable conserva ambos`() {
        val raw = """
          ALARM 1 FAULT CRITICAL 0x2e11a001 SERVICE QUALITY 2026-07-31 10:00:00-05:00
          ALARM NAME  : The feeder fiber is broken or OLT can not receive any expected optical signals(LOS)
          PARAMETERS  : FrameID: 0, SlotID: 0, PortID: 3
          --- END
          totally broken fragment without fields
          --- END
        """.trimIndent()

        val alarms = parser.parseActiveAlarms(raw)

        assertEquals(2, alarms.size)
        assertEquals("PON_PORT_DOWN", alarms[0].reasonCode)
        assertEquals("OLT_ALARM_UNPARSED", alarms[1].reasonCode)
        assertTrue(alarms[1].rawBlock.contains("totally broken fragment"))
    }
}
