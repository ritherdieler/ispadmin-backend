package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.servicehealth.port.HealthOnuRef

object LabOpticalTargets {
    data class Target(val subscriptionId: Int, val onu: HealthOnuRef)

    fun resolve(ids: Collection<Int>, onuOf: (Int) -> HealthOnuRef?): List<Target> =
        ids.mapNotNull { id -> onuOf(id)?.let { Target(id, it) } }
}
