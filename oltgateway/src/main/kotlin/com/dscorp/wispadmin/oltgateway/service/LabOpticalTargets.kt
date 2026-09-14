package com.dscorp.wispadmin.oltgateway.service

data class LabOnuRef(
    val id: Long,
    val sn: String,
    val oltId: Long?,
    val board: Int,
    val port: Int,
    val onuIndex: Int,
)

object LabOpticalTargets {
    data class Target(val subscriptionId: Int, val onu: LabOnuRef)

    fun resolve(ids: Collection<Int>, onuOf: (Int) -> LabOnuRef?): List<Target> =
        ids.mapNotNull { id -> onuOf(id)?.let { Target(id, it) } }
}
