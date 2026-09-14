package com.dscorp.wispadmin.wispadmin.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CrmAccessPolicyTest {

    @Test
    fun `permite SECRETARY y ADMIN`() {
        assertTrue(CrmAccessPolicy.canAccessWhatsAppOrCrm("SECRETARY"))
        assertTrue(CrmAccessPolicy.canAccessWhatsAppOrCrm("ADMIN"))
        assertTrue(CrmAccessPolicy.canAccessWhatsAppOrCrm("admin"))
        assertTrue(CrmAccessPolicy.canAccessWhatsAppOrCrm(" secretary "))
    }

    @Test
    fun `deniega roles no autorizados y null`() {
        assertFalse(CrmAccessPolicy.canAccessWhatsAppOrCrm(null))
        assertFalse(CrmAccessPolicy.canAccessWhatsAppOrCrm(""))
        assertFalse(CrmAccessPolicy.canAccessWhatsAppOrCrm("TECHNICIAN"))
        assertFalse(CrmAccessPolicy.canAccessWhatsAppOrCrm("SALES"))
        assertFalse(CrmAccessPolicy.canAccessWhatsAppOrCrm("CLIENT"))
        assertFalse(CrmAccessPolicy.canAccessWhatsAppOrCrm("ACCOUNTANT"))
    }

    @Test
    fun `solo ADMIN gestiona secretos CRM`() {
        assertTrue(CrmAccessPolicy.canManageCrmSecrets("ADMIN"))
        assertTrue(CrmAccessPolicy.canManageCrmSecrets("admin"))
        assertFalse(CrmAccessPolicy.canManageCrmSecrets("SECRETARY"))
        assertFalse(CrmAccessPolicy.canManageCrmSecrets(null))
    }

    @Test
    fun `protege whatsapp y crm excepto webhook`() {
        assertTrue(CrmAccessPolicy.isProtectedPath("/whatsapp/conversations"))
        assertTrue(CrmAccessPolicy.isProtectedPath("/whatsapp"))
        assertTrue(CrmAccessPolicy.isProtectedPath("/crm/conversations"))
        assertTrue(CrmAccessPolicy.isProtectedPath("/crm"))
        assertFalse(CrmAccessPolicy.isProtectedPath("/whatsapp/webhook"))
        assertFalse(CrmAccessPolicy.isProtectedPath("/users/login"))
        assertFalse(CrmAccessPolicy.isProtectedPath("/api/netdiag/health"))
    }
}
