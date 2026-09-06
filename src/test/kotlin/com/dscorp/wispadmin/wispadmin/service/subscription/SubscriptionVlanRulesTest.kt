package com.dscorp.wispadmin.wispadmin.service.subscription

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SubscriptionVlanRulesTest {

    @Test
    fun `requireAppVlan acepta 1 y 100`() {
        assertEquals("1", SubscriptionVlanRules.requireAppVlan("1"))
        assertEquals("100", SubscriptionVlanRules.requireAppVlan(" 100 "))
    }

    @Test
    fun `requireAppVlan falla si null o vacio`() {
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionVlanRules.requireAppVlan(null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionVlanRules.requireAppVlan("   ")
        }
    }

    @Test
    fun `requireAppVlan falla si valor no permitido`() {
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionVlanRules.requireAppVlan("50")
        }
    }

    @Test
    fun `assertPoolAligned VLAN 100 exige pool prod 30 o 31`() {
        SubscriptionVlanRules.assertPoolAligned("100", "192.168.30.0/24")
        SubscriptionVlanRules.assertPoolAligned("100", "192.168.31.1/24")
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionVlanRules.assertPoolAligned("100", "192.168.123.0/24")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionVlanRules.assertPoolAligned("100", "192.168.32.1/24")
        }
    }

    @Test
    fun `assertPoolAligned VLAN 100 acepta pool staging 250 solo con tag stg`() {
        SubscriptionVlanRules.assertPoolAligned("100", "192.168.250.1/24", environmentTag = "stg")
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionVlanRules.assertPoolAligned("100", "192.168.250.1/24")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionVlanRules.assertPoolAligned("100", "192.168.250.1/24", environmentTag = "prod")
        }
    }

    @Test
    fun `assertPoolAligned VLAN 1 rechaza pools VLAN100 prod`() {
        SubscriptionVlanRules.assertPoolAligned("1", "192.168.255.0/24")
        SubscriptionVlanRules.assertPoolAligned("1", "192.168.123.0/24")
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionVlanRules.assertPoolAligned("1", "192.168.30.0/24")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionVlanRules.assertPoolAligned("1", "192.168.31.1/24")
        }
    }

    @Test
    fun `assertPoolAligned no-op sin segmento`() {
        SubscriptionVlanRules.assertPoolAligned("100", null)
        SubscriptionVlanRules.assertPoolAligned("1", "  ")
    }

    @Test
    fun `resolveMigrationVlan usa vlan de la app cuando viene`() {
        assertEquals("100", SubscriptionVlanRules.resolveMigrationVlan("100"))
        assertEquals("1", SubscriptionVlanRules.resolveMigrationVlan(" 1 "))
    }

    @Test
    fun `resolveMigrationVlan default 100 si app no envia vlan`() {
        assertEquals("100", SubscriptionVlanRules.resolveMigrationVlan(null))
        assertEquals("100", SubscriptionVlanRules.resolveMigrationVlan(""))
        assertEquals("100", SubscriptionVlanRules.resolveMigrationVlan("   "))
    }

    @Test
    fun `resolveMigrationVlan rechaza vlan no permitida`() {
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionVlanRules.resolveMigrationVlan("50")
        }
    }
}
