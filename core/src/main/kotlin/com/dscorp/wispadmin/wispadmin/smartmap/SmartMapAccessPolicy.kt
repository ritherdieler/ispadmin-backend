package com.dscorp.wispadmin.wispadmin.smartmap

object SmartMapAccessPolicy {
    private val DEBT_RESTRICTED_ROLES = setOf("TECHNICIAN", "SALES")
    private val TICKET_RESTRICTED_ROLES = setOf("ACCOUNTANT", "SALES")
    private val INTELLIGENCE_MANAGER_ROLES = setOf("ADMIN", "SALES")

    fun normalizeUserType(userType: String?): String? = userType?.trim()?.uppercase()

    fun canViewDebt(userType: String?): Boolean = normalizeUserType(userType) !in DEBT_RESTRICTED_ROLES

    fun canViewTickets(userType: String?): Boolean = normalizeUserType(userType) !in TICKET_RESTRICTED_ROLES

    fun canManageIntelligence(userType: String?): Boolean {
        val normalized = normalizeUserType(userType) ?: return true
        return normalized in INTELLIGENCE_MANAGER_ROLES
    }
}
