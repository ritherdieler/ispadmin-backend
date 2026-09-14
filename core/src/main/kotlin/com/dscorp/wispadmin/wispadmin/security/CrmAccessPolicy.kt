package com.dscorp.wispadmin.wispadmin.security

object CrmAccessPolicy {

    private val ALLOWED_ROLES = setOf("SECRETARY", "ADMIN")

    fun normalizeUserType(userType: String?): String? =
        userType?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }

    fun canAccessWhatsAppOrCrm(userType: String?): Boolean =
        normalizeUserType(userType) in ALLOWED_ROLES

    fun canManageCrmSecrets(userType: String?): Boolean =
        normalizeUserType(userType) == "ADMIN"

    fun isProtectedPath(path: String): Boolean {
        val normalized = path.trimEnd('/')
        if (normalized.endsWith("/whatsapp/webhook") || normalized.contains("/whatsapp/webhook/")) {
            return false
        }
        if (normalized.endsWith("/whatsapp") || normalized.contains("/whatsapp/")) {
            return true
        }
        if (normalized.endsWith("/crm") || normalized.contains("/crm/")) {
            return true
        }
        return false
    }
}
