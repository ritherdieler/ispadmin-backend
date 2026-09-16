package com.dscorp.wispadmin.wispadmin.service

class SmartOltMgmtVlanPolicy(
    private val customerVlans: Set<String>,
    private val mgmtVlan: String,
) {
    fun mgmtVlanForCustomerVlan(customerVlan: String): String? {
        val normalized = customerVlan.trim()
        if (normalized.isEmpty() || normalized !in customerVlans) return null
        return mgmtVlan.trim().takeIf { it.isNotEmpty() }
    }
}
