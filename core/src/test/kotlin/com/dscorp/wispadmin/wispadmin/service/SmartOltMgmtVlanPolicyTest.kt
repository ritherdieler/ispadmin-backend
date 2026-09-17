package com.dscorp.wispadmin.wispadmin.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

class SmartOltMgmtVlanPolicyTest {

    private val policy = SmartOltMgmtVlanPolicy(
        customerVlans = setOf("100"),
        mgmtVlan = "1000",
    )

    @Test
    fun `vlan 100 pide mgmt vlan 1000`() {
        assertEquals("1000", policy.mgmtVlanForCustomerVlan("100"))
        assertEquals("1000", policy.mgmtVlanForCustomerVlan(" 100 "))
    }

    @Test
    fun `otras vlan no abren mgmt`() {
        assertNull(policy.mgmtVlanForCustomerVlan("1"))
        assertNull(policy.mgmtVlanForCustomerVlan("1100"))
        assertNull(policy.mgmtVlanForCustomerVlan(""))
    }

    @Test
    fun `class is open so staging CGLIB can proxy the bean`() {
        assertFalse(Modifier.isFinal(SmartOltMgmtVlanPolicy::class.java.modifiers))
    }
}
