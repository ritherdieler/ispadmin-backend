package com.dscorp.wispadmin.oltgateway.snmp

import java.util.concurrent.ConcurrentHashMap

/**
 * One SNMP semaphore per OLT. Two MA5608T boxes can walk at the same time;
 * each box is still limited by its model (MA5608T = 1).
 */
class OltSnmpBusRegistry(
    private val acquireTimeoutMs: Long,
    private val maxWalksForModel: (String?) -> Int = OltSnmpModelLimits::maxConcurrentWalks
) {
    private val buses = ConcurrentHashMap<String, OltSnmpBus>()

    fun forOlt(oltId: String, modelCode: String): OltSnmpBus {
        val key = oltId.ifBlank { "default" }
        return buses.computeIfAbsent(key) {
            OltSnmpBus(
                oltId = key,
                maxConcurrent = maxWalksForModel(modelCode).coerceAtLeast(1),
                acquireTimeoutMs = acquireTimeoutMs
            )
        }
    }
}
