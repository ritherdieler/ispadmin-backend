package com.dscorp.wispadmin.netdiag.port

data class OntSubscriptionInfo(
    val subscriptionId: Int?,
    val customerName: String?,
    val serviceStatus: String?,
    val napBoxCode: String?
)

interface NetDiagOntSubscriptionPort {
    fun findActiveByOnuSn(sn: String): OntSubscriptionInfo?
}
