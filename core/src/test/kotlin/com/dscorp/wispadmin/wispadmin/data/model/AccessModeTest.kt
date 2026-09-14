package com.dscorp.wispadmin.wispadmin.data.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AccessModeTest {

    @Test
    fun `there are exactly three access modes`() {
        assertEquals(
            listOf(AccessMode.STATIC_IP, AccessMode.PPPOE_FIXED, AccessMode.PPPOE_DYNAMIC),
            AccessMode.entries.toList()
        )
    }

    @Test
    fun `both pppoe modes authenticate over pppoe`() {
        assertFalse(AccessMode.STATIC_IP.usesPppoe())
        assertTrue(AccessMode.PPPOE_FIXED.usesPppoe())
        assertTrue(AccessMode.PPPOE_DYNAMIC.usesPppoe())
    }

    @Test
    fun `pppoe fixed keeps a stable ip address like a static client`() {
        assertTrue(AccessMode.STATIC_IP.usesStaticIpAddress())
        assertTrue(AccessMode.PPPOE_FIXED.usesStaticIpAddress())
        assertFalse(AccessMode.PPPOE_DYNAMIC.usesStaticIpAddress())
    }

    @Test
    fun `only dynamic pppoe drops the simple queue`() {
        assertTrue(AccessMode.STATIC_IP.usesSimpleQueue())
        assertTrue(AccessMode.PPPOE_FIXED.usesSimpleQueue())
        assertFalse(AccessMode.PPPOE_DYNAMIC.usesSimpleQueue())
    }

    @Test
    fun `only dynamic pppoe is cut by profile instead of address list`() {
        assertTrue(AccessMode.STATIC_IP.usesAddressListCut())
        assertTrue(AccessMode.PPPOE_FIXED.usesAddressListCut())
        assertFalse(AccessMode.PPPOE_DYNAMIC.usesAddressListCut())
    }
}
