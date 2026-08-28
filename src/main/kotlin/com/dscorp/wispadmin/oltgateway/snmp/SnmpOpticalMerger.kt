package com.dscorp.wispadmin.oltgateway.snmp

object SnmpOpticalMerger {

    fun merge(
        rx: Map<SnmpOntKey, Double?>,
        tx: Map<SnmpOntKey, Double?>,
        oltRx: Map<SnmpOntKey, Double?>,
        temperatureC: Map<SnmpOntKey, Double?> = emptyMap(),
        biasCurrentMa: Map<SnmpOntKey, Double?> = emptyMap(),
        distanceM: Map<SnmpOntKey, Int?> = emptyMap(),
        matchState: Map<SnmpOntKey, String?> = emptyMap()
    ): List<SnmpOntOptical> {
        val keys = rx.keys + tx.keys + oltRx.keys +
            temperatureC.keys + biasCurrentMa.keys + distanceM.keys + matchState.keys
        return keys.map { key ->
            SnmpOntOptical(
                key = key,
                onuRxDbm = rx[key],
                onuTxDbm = tx[key],
                oltRxDbm = oltRx[key],
                temperatureC = temperatureC[key],
                biasCurrentMa = biasCurrentMa[key],
                distanceM = distanceM[key],
                matchState = matchState[key]
            )
        }
    }
}
