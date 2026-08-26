package com.dscorp.wispadmin.wispadmin.service.genieacs

import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GenieAcsSubscriptionTagsTest {

    @Test
    fun `serviceKind is TV for ONLY_TV_FIBER`() {
        assertEquals(
            GenieAcsServiceKind.TV,
            GenieAcsSubscriptionTags.serviceKind(
                installationType = InstallationType.ONLY_TV_FIBER,
                planType = InstallationType.ONLY_TV_FIBER,
                planName = "Internet 100",
            ),
        )
    }

    @Test
    fun `serviceKind is DUO when plan name contains duo or combo`() {
        assertEquals(
            GenieAcsServiceKind.DUO,
            GenieAcsSubscriptionTags.serviceKind(
                installationType = InstallationType.FIBER,
                planType = InstallationType.FIBER,
                planName = "Duo 200 Mbps",
            ),
        )
        assertEquals(
            GenieAcsServiceKind.DUO,
            GenieAcsSubscriptionTags.serviceKind(
                installationType = InstallationType.FIBER,
                planType = InstallationType.FIBER,
                planName = "Combo Internet + TV",
            ),
        )
    }

    @Test
    fun `serviceKind is INTERNET for remaining FIBER plans`() {
        assertEquals(
            GenieAcsServiceKind.INTERNET,
            GenieAcsSubscriptionTags.serviceKind(
                installationType = InstallationType.FIBER,
                planType = InstallationType.FIBER,
                planName = "Fibra 100 Mbps",
            ),
        )
    }

    @Test
    fun `wanConnectionName is id tipo nombre uppercase without accents`() {
        assertEquals(
            "744 INTERNET JUAN PEREZ",
            GenieAcsSubscriptionTags.wanConnectionName(
                subscriptionId = 744,
                kind = GenieAcsServiceKind.INTERNET,
                fullName = "Juan Pérez",
            ),
        )
    }

    @Test
    fun `wanConnectionName truncates name not id or type to 64 chars`() {
        val name = GenieAcsSubscriptionTags.wanConnectionName(
            subscriptionId = 744,
            kind = GenieAcsServiceKind.INTERNET,
            fullName = "Maria Jose Garcia Lopez De La Torre Y Fernandez Extra Largo",
        )
        assertTrue(name.length <= 64)
        assertTrue(name.length >= 50)
        assertTrue(name.startsWith("744 INTERNET "))
        assertFalse(name.contains("/"))
    }

    @Test
    fun `wanConnectionName strips path-breaking characters`() {
        assertEquals(
            "12 TV CARLOS LOPEZ",
            GenieAcsSubscriptionTags.wanConnectionName(
                subscriptionId = 12,
                kind = GenieAcsServiceKind.TV,
                fullName = "Carlos / Lopez?#&%",
            ),
        )
    }

    @Test
    fun `managed tags use prefixes sub t and c`() {
        val tags = GenieAcsSubscriptionTags.managedTags(
            subscriptionId = 744,
            kind = GenieAcsServiceKind.DUO,
            fullName = "María García",
        )
        assertEquals(
            listOf("sub-744", "t:DUO", "c:MARIA GARCIA"),
            tags,
        )
    }

    @Test
    fun `tagsToRemove drops stale managed prefixes keeping others`() {
        val stale = GenieAcsSubscriptionTags.tagsToRemove(
            existing = listOf("sub-10", "t:INTERNET", "c:OLD NAME", "lab", "sub-744"),
            desired = listOf("sub-744", "t:DUO", "c:JUAN PEREZ"),
        )
        assertEquals(setOf("sub-10", "t:INTERNET", "c:OLD NAME"), stale.toSet())
    }

    @Test
    fun `isManagedTag recognizes only our prefixes`() {
        assertTrue(GenieAcsSubscriptionTags.isManagedTag("sub-1"))
        assertTrue(GenieAcsSubscriptionTags.isManagedTag("t:TV"))
        assertTrue(GenieAcsSubscriptionTags.isManagedTag("c:JUAN"))
        assertFalse(GenieAcsSubscriptionTags.isManagedTag("lab"))
        assertFalse(GenieAcsSubscriptionTags.isManagedTag("provisioned"))
    }
}
