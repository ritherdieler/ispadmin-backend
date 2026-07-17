package com.dscorp.wispadmin.oltgateway.service.inventory

object InventoryJobPlanner {

    fun plan(slots: List<GponSlotInfo>, maxSessions: Int): List<InventoryCliJob> {
        if (slots.isEmpty() || maxSessions < 1) {
            return emptyList()
        }
        return listOf(InventoryCliJob.FrameAll)
    }
}
