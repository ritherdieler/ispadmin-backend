package com.dscorp.wispadmin.oltgateway.service

object OntIdAllocator {
    const val MAX_ONT_ID = 127

    fun nextFree(liveOccupied: Set<Int>, dbMax: Int, maxOntId: Int = MAX_ONT_ID): Int {
        val fromDb = if (dbMax >= 0) (0..dbMax).toSet() else emptySet()
        val occupied = liveOccupied + fromDb
        return (0..maxOntId).firstOrNull { it !in occupied }
            ?: throw IllegalStateException("No free ONT ID left on this PON port")
    }
}
