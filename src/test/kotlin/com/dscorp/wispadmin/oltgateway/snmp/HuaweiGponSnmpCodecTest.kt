package com.dscorp.wispadmin.oltgateway.snmp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HuaweiGponSnmpCodecTest {

    @Test
    fun `decodifica ifIndex FA000000 a frame 0 slot 0 port 0`() {
        val fsp = HuaweiGponSnmpCodec.decodeIfIndex(4_194_304_000L)
        assertEquals(0, fsp.frame)
        assertEquals(0, fsp.slot)
        assertEquals(0, fsp.port)
    }

    @Test
    fun `decodifica ifIndex slot1 port6`() {
        val fsp = HuaweiGponSnmpCodec.decodeIfIndex(4_194_313_728L)
        assertEquals(0, fsp.frame)
        assertEquals(1, fsp.slot)
        assertEquals(6, fsp.port)
    }

    @Test
    fun `encode ifIndex roundtrip`() {
        val ifIndex = HuaweiGponSnmpCodec.encodeIfIndex(slot = 1, port = 2)
        assertEquals(4_194_312_704L, ifIndex)
        assertEquals(GponFsp(0, 1, 2), HuaweiGponSnmpCodec.decodeIfIndex(ifIndex))
    }

    @Test
    fun `serial 8 bytes a SN estilo CLI`() {
        val bytes = byteArrayOf(
            0x56, 0x53, 0x4F, 0x4C.toByte(),
            0x00, 0x86.toByte(), 0xF6.toByte(), 0xE9.toByte()
        )
        assertEquals("VSOL0086F6E9", HuaweiGponSnmpCodec.decodeOntSn(bytes))
    }

    @Test
    fun `normalize hex CLI de 16 chars a vendor+suffix`() {
        assertEquals("VSOL0086F6E9", HuaweiGponSnmpCodec.normalizeOntSn("56534F4C0086F6E9"))
        assertEquals("HWTC15F5B736", HuaweiGponSnmpCodec.normalizeOntSn("4857544315F5B736"))
    }

    @Test
    fun `normalize deja vendor+suffix en mayusculas`() {
        assertEquals("VSOL0086F6E9", HuaweiGponSnmpCodec.normalizeOntSn("vsol0086f6e9"))
    }

    @Test
    fun `decodeIfIndex acepta ifIndex signed de OID SNMP4j`() {
        // 0xFA000000 as signed Int → -100663296 (common snmp4j OID.get)
        assertEquals(GponFsp(0, 0, 0), HuaweiGponSnmpCodec.decodeIfIndex(-100663296L))
        assertEquals(GponFsp(0, 1, 0), HuaweiGponSnmpCodec.decodeIfIndex(-100655104L))
    }

    @Test
    fun `serial HWTC`() {
        val bytes = byteArrayOf(
            0x48, 0x57, 0x54, 0x43,
            0x15, 0xF5.toByte(), 0xB7.toByte(), 0x36
        )
        assertEquals("HWTC15F5B736", HuaweiGponSnmpCodec.decodeOntSn(bytes))
    }

    @Test
    fun `run status 1 online 2 offline`() {
        assertEquals("online", HuaweiGponSnmpCodec.decodeRunState(1))
        assertEquals("offline", HuaweiGponSnmpCodec.decodeRunState(2))
        assertNull(HuaweiGponSnmpCodec.decodeRunState(0))
    }

    @Test
    fun `potencia ONT Rx Tx divide por 100`() {
        assertEquals(-5.72, HuaweiGponSnmpCodec.decodeOntPowerDbm(-572)!!, 0.001)
        assertEquals(1.63, HuaweiGponSnmpCodec.decodeOntPowerDbm(163)!!, 0.001)
    }

    @Test
    fun `potencia OLT Rx usa offset 100 dBm`() {
        // Live sample 7255 → ~-27.45 dBm (plausible OLT RX)
        assertEquals(-27.45, HuaweiGponSnmpCodec.decodeOltRxPowerDbm(7255)!!, 0.001)
    }

    @Test
    fun `sentinel potencias invalidas`() {
        assertNull(HuaweiGponSnmpCodec.decodeOntPowerDbm(2147483647))
        assertNull(HuaweiGponSnmpCodec.decodeOltRxPowerDbm(2147483647))
    }
}
