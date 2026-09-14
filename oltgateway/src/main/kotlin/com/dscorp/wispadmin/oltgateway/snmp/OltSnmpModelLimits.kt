package com.dscorp.wispadmin.oltgateway.snmp

/**
 * Max concurrent SNMP GET/GETBULK the OLT agent survives, keyed by model.
 * Measured live: MA5608T drops overlapping walks (OLT-Rx timeout with 3 parallel columns).
 * Unknown models stay at 1 until measured.
 */
object OltSnmpModelLimits {
    const val DEFAULT_MAX_CONCURRENT_WALKS = 1

    fun maxConcurrentWalks(modelCode: String?): Int {
        return when (modelCode?.trim()?.uppercase()) {
            "MA5608T" -> 1
            else -> DEFAULT_MAX_CONCURRENT_WALKS
        }
    }
}
