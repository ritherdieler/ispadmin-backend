package com.dscorp.wispadmin.oltgateway.service.inventory

data class GponSlotInfo(
    val slot: Int,
    val boardName: String,
    val portCount: Int
)

sealed class InventoryCliJob {
    data object FrameAll : InventoryCliJob()
    data class SlotAll(val slot: Int) : InventoryCliJob()
    data class SlotPort(val slot: Int, val port: Int) : InventoryCliJob()

    fun command(): String = when (this) {
        is FrameAll -> "display ont info 0 all"
        is SlotAll -> "display ont info 0 $slot all"
        is SlotPort -> "display ont info 0 $slot $port all"
    }
}
