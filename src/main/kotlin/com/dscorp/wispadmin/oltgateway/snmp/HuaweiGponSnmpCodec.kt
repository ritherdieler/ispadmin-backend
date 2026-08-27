package com.dscorp.wispadmin.oltgateway.snmp

/**
 * Codec for Huawei MA5608T GPON SNMP indexes and values verified on
 * MA5600V800R015C00 / SPH106 (2026-08-26). See olt-ma5608t-snmp-capabilities.md.
 */
object HuaweiGponSnmpCodec {

    private const val IFINDEX_BASE = 0xFA000000L
    private const val INVALID_POWER = 2147483647
    private val HEX_SN = Regex("^[0-9A-F]{16}$")

    fun encodeIfIndex(slot: Int, port: Int, frame: Int = 0): Long {
        require(frame == 0) { "MA5608T probe used frame=0 only" }
        return IFINDEX_BASE + (slot.toLong() shl 13) + (port.toLong() shl 8)
    }

    fun decodeIfIndex(ifIndex: Long): GponFsp {
        val unsigned = ifIndex and 0xFFFFFFFFL
        val slot = ((unsigned shr 13) and 0x3F).toInt()
        val port = ((unsigned shr 8) and 0x1F).toInt()
        return GponFsp(frame = 0, slot = slot, port = port)
    }

    fun decodeOntSn(bytes: ByteArray): String {
        require(bytes.size >= 8) { "ONT SN must be 8 bytes" }
        val vendor = bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII)
        val suffix = bytes.copyOfRange(4, 8).joinToString("") { b ->
            String.format("%02X", b.toInt() and 0xFF)
        }
        return vendor + suffix
    }

    /**
     * Canonical ONT SN is vendor ASCII + 8 hex (e.g. VSOL0086F6E9).
     * CLI `display ont info summary` often shows the same 8 bytes as 16 hex digits
     * (56534F4C0086F6E9); normalize both forms for DB matching.
     */
    fun normalizeOntSn(raw: String): String {
        val s = raw.trim().uppercase()
        if (s.length == 16 && HEX_SN.matches(s)) {
            val bytes = ByteArray(8) { i ->
                s.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            return decodeOntSn(bytes)
        }
        return s
    }

    fun decodeRunState(raw: Int): String? = when (raw) {
        1 -> "online"
        2 -> "offline"
        else -> null
    }

    /** ONT Rx/Tx: SNMP integer in 0.01 dBm units. */
    fun decodeOntPowerDbm(raw: Int): Double? {
        if (raw == INVALID_POWER) return null
        return raw / 100.0
    }

    /**
     * OLT Rx of ONT: live samples (e.g. 7255) map to plausible dBm as `(raw/100) - 100`.
     * Confirm against CLI before exposing in UI charts.
     */
    fun decodeOltRxPowerDbm(raw: Int): Double? {
        if (raw == INVALID_POWER) return null
        return (raw / 100.0) - 100.0
    }
}

data class GponFsp(
    val frame: Int,
    val slot: Int,
    val port: Int
)
