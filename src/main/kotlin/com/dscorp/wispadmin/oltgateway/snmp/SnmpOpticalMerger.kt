package com.dscorp.wispadmin.oltgateway.snmp

/**
 * Merges per-column SNMP walks into [SnmpOntOptical] rows keyed by ifIndex + ontId.
 */
object SnmpOpticalMerger {

    fun merge(
        rx: Map<SnmpOntKey, Double?>,
        tx: Map<SnmpOntKey, Double?>,
        oltRx: Map<SnmpOntKey, Double?>
    ): List<SnmpOntOptical> {
        val keys = rx.keys + tx.keys + oltRx.keys
        return keys.map { key ->
            SnmpOntOptical(
                key = key,
                onuRxDbm = rx[key],
                onuTxDbm = tx[key],
                oltRxDbm = oltRx[key]
            )
        }
    }
}
