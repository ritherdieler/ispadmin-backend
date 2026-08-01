package com.dscorp.wispadmin.oltgateway.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class HuaweiOltAlarmParserTest {

    private val parser = HuaweiOltAlarmParser()

    @Test
    fun `parsea alarmas activas live con slot port ont y reason codes`() {
        val raw = javaClass.getResource("/olt/alarm-active-sample.txt")!!.readText()

        val alarms = parser.parseActiveAlarms(raw)

        assertEquals(4, alarms.size)

        val dying = alarms.first { it.alarmIdHex == "0x2e11a00b" }
        assertEquals("The dying-gasp of GPON ONTi (DGi) is generated", dying.alarmName)
        assertEquals(1, dying.slotId)
        assertEquals(6, dying.portId)
        assertEquals(54, dying.ontId)
        assertEquals("ONT_DYING_GASP", dying.reasonCode)
        assertEquals("P2", dying.severity)
        assertEquals("gpon-1/6", dying.component)
        assertFalse(dying.isClear)

        val losi = alarms.first { it.alarmIdHex == "0x2e112007" }
        assertEquals(1, losi.slotId)
        assertEquals(8, losi.portId)
        assertEquals(2, losi.ontId)
        assertEquals("ONT_OFFLINE", losi.reasonCode)
        assertEquals("P1", losi.severity)
        assertTrue(losi.alarmName.contains("LOSi/LOBi"))

        val configFail = alarms.first { it.portId == 5 }
        assertEquals("ONT_CONFIG_RECOVERY_FAIL", configFail.reasonCode)
        assertEquals("P2", configFail.severity)
    }

    @Test
    fun `texto sin estructura se conserva como unparsed en vez de descartarse`() {
        val alarms = parser.parseActiveAlarms("No alarm")
        assertEquals(1, alarms.size)
        assertEquals("OLT_ALARM_UNPARSED", alarms[0].reasonCode)
        assertEquals("No alarm", alarms[0].rawBlock)
    }

    @Test
    fun `raw en blanco no genera eventos`() {
        assertTrue(parser.parseActiveAlarms("").isEmpty())
        assertTrue(parser.parseActiveAlarms("   \n  ").isEmpty())
    }

    @Test
    fun `mapea LOS feeder a PON_PORT_DOWN sin ontId`() {
        val raw = """
          ALARM 1 FAULT CRITICAL 0x2e11a001 SERVICE QUALITY 2026-07-31 10:00:00-05:00
          ALARM NAME  : The feeder fiber is broken or OLT can not receive any expected optical signals(LOS)
          PARAMETERS  : FrameID: 0, SlotID: 0, PortID: 3
          DESCRIPTION : Feeder LOS
          --- END
        """.trimIndent()

        val alarms = parser.parseActiveAlarms(raw)
        assertEquals(1, alarms.size)
        assertEquals("PON_PORT_DOWN", alarms[0].reasonCode)
        assertEquals("P0", alarms[0].severity)
        assertNull(alarms[0].ontId)
        assertEquals("gpon-0/3", alarms[0].component)
        assertFalse(alarms[0].isClear)
    }

    @Test
    fun `mapea recover LOSi como clear del mismo reasonCode`() {
        val raw = """
          ALARM 2 FAULT WARNING 0x2e122007 SERVICE QUALITY 2026-07-31 11:00:00-05:00
          ALARM NAME  : OLT can receive expected optical signals from ONT(LOSi/LOBi) recovers
          PARAMETERS  : FrameID: 0, SlotID: 1, PortID: 8, ONT ID: 2
          --- END
        """.trimIndent()

        val alarms = parser.parseActiveAlarms(raw)
        assertEquals(1, alarms.size)
        assertEquals("ONT_OFFLINE", alarms[0].reasonCode)
        assertTrue(alarms[0].isClear)
    }

    @Test
    fun `mapea hardware de puerto GPON y board sin port a componentes correctos`() {
        val portHw = """
          ALARM 3 FAULT CRITICAL 0x2e11a002 EQUIPMENT 2026-07-31 12:00:00-05:00
          ALARM NAME  : The hardware of the GPON port is faulty
          PARAMETERS  : FrameID: 0, SlotID: 0, PortID: 7
          --- END
        """.trimIndent()
        val board = parser.parseActiveAlarms(portHw).single()
        assertEquals("PON_PORT_HW_FAULT", board.reasonCode)
        assertEquals("P0", board.severity)
        assertEquals("gpon-0/7", board.component)

        val chassis = """
          ALARM 4 FAULT CRITICAL 0x02310001 EQUIPMENT 2026-07-31 12:01:00-05:00
          ALARM NAME  : The board is failed
          PARAMETERS  : FrameID: 0, SlotID: 1
          --- END
        """.trimIndent()
        val boardAlarm = parser.parseActiveAlarms(chassis).single()
        assertEquals("OLT_BOARD_FAULT", boardAlarm.reasonCode)
        assertEquals("board-1", boardAlarm.component)
    }

    @ParameterizedTest
    @CsvSource(
        "0x2e11a001, The feeder fiber is broken LOS, PON_PORT_DOWN, false",
        "0x2e12a001, OLT can receive expected optical signals from ONTs LOS recovers, PON_PORT_DOWN, true",
        "0x2e112007, distribute fiber LOSi/LOBi, ONT_OFFLINE, false",
        "0x2e122007, LOSi/LOBi recovers, ONT_OFFLINE, true",
        "0x2e11a00b, dying-gasp of GPON ONTi, ONT_DYING_GASP, false",
        "0x2e12a00b, dying-gasp of GPON ONTi recovers, ONT_DYING_GASP, true",
        "0x2e112006, loss of frame of ONTi LOFi, ONT_LOFI, false",
        "0x2e112004, signal fail of ONTi SFi, ONT_SFI, false",
        "0x2e112003, signal degrade of ONTi SDi, ONT_SDI, false",
        "0x2e112002, loss of GEM channel delineation LCDGi, ONT_LCDGI, false",
        "0x2e112001, The RDIi occurs, ONT_RDI, false",
        "0x2e11a00c, loss of PLOAM of ONTi LOAMi, ONT_LOAMI, false",
        "0x2e11a009, deactivation failure of ONTi DFi, ONT_DFI, false",
        "0x2e11a00f, physical equipment error of ONTi PEEi, ONT_PEE, false",
        "0x2e111999, ONT initiative to go offline, ONT_INITIATIVE_OFFLINE, false",
        "0x2e305015, authentication information about the ONT is invalid, ONT_AUTH_INVALID, false",
        "0x2e21a102, GPON ONT configuration recovery fails, ONT_CONFIG_RECOVERY_FAIL, false",
        "0x2e22a102, GPON ONT configuration recovery is successful, ONT_CONFIG_RECOVERY_FAIL, true",
        "0x2e314021, illegal incursionary rogue ONTs under the port, PON_ROGUE_ONT, false",
        "0x2e314022, The ONT is rogue ONT, PON_ROGUE_ONT, false",
        "0x2e11a002, hardware of the GPON port is faulty, PON_PORT_HW_FAULT, false",
        "0x2e314020, optical transceiver of the PON port is absent, PON_OPTICS_ABSENT, false",
        "0x2e11999c, ONTs under this port failed in ranging, PON_RANGING_FAIL, false",
        "0x2e31305f, Numerous ONTs connected to the PON port are powered off, PON_MASS_POWER_OFF, false",
        "0x2e11a523, backbone fiber on the port in Type B protection, PON_PROTECTION_FIBER, false",
        "0x2e11999e, downstream signal degrade SD of the ONT, ONT_DOWNSTREAM_SD, false",
        "0x2e11999f, downstream signal fail SF of the ONT, ONT_DOWNSTREAM_SF, false",
        "0x2e31305c, Local optical transceiver parameters exceed alarm, ONT_OPTICAL_ALARM, false",
        "0x2e31305e, Remote optical transceiver parameters exceed alarm, ONT_OPTICAL_ALARM, false",
        "0x2e313060, Local optical transceiver parameters exceed warning, ONT_OPTICAL_WARNING, false",
        "0x2e313062, Remote optical transceiver parameters exceed warning, ONT_OPTICAL_WARNING, false",
        "0x2e313015, hardware of the ONT is faulty, ONT_HW_FAULT, false",
        "0x2e313024, loss of signals occurs on the ethernet port of the ONT, ONT_ETH_LOS, false",
        "0x2e313016, ONT switches to the standby battery, ONT_BATTERY, false",
        "0x2e313017, standby battery of the ONT is lost, ONT_BATTERY, false",
        "0x2e313018, standby battery of the ONT cannot be charged, ONT_BATTERY, false",
        "0x2e313019, voltage of the standby battery of the ONT is too low, ONT_BATTERY, false",
        "0x2e11a524, number of ONT DOWis exceeds the alarm threshold, ONT_DOWI_THRESHOLD, false",
        "0x2e112009, FEC downstream correctable code words exceed, ONT_FEC_CORRECTABLE, false",
        "0x2e11200a, FEC downstream uncorrectable code words exceed, ONT_FEC_UNCORRECTABLE, false",
        "0x2e11a104, number of ONT LOOCis exceeds the alarm threshold, ONT_LOOCI_THRESHOLD, false"
    )
    fun `clasifica alarmas GPON diagnosticas por ID`(
        alarmId: String,
        alarmName: String,
        expectedReason: String,
        expectedClear: Boolean
    ) {
        val mapped = parser.mapReasonCode(alarmName, alarmId)
        val clear = parser.isClearAlarm(alarmName, alarmId)
        assertEquals(expectedReason, mapped)
        assertEquals(expectedClear, clear)
    }

    @ParameterizedTest
    @CsvSource(
        "The power module is abnormal, OLT_POWER_FAULT",
        "The fan is failed, OLT_FAN_FAULT",
        "The temperature of the board is too high, OLT_TEMP_HIGH",
        "The board is failed, OLT_BOARD_FAULT",
        "The control board is failed, OLT_CONTROL_BOARD_FAULT",
        "The uplink port is down, OLT_UPLINK_DOWN"
    )
    fun `clasifica fallas hardware chasis OLT por nombre`(alarmName: String, expectedReason: String) {
        assertEquals(expectedReason, parser.mapReasonCode(alarmName, null))
        assertFalse(parser.isClearAlarm(alarmName, null))
    }
}
