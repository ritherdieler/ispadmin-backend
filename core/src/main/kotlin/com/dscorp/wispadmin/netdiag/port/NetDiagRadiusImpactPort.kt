package com.dscorp.wispadmin.netdiag.port

data class RadiusImpactSnapshot(
    val source: String,
    val activeSubscriptions: Long,
    val pppActiveSessions: Int?,
    val estimatedAffected: Long?,
    val notes: String?
)

interface NetDiagRadiusImpactPort {
    fun estimateImpact(targetId: Long?, deviceRefId: Long?): RadiusImpactSnapshot
}
