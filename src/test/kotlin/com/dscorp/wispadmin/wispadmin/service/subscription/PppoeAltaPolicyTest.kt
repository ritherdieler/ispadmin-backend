package com.dscorp.wispadmin.wispadmin.service.subscription

import com.dscorp.wispadmin.wispadmin.data.model.AccessMode
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.PppoeProvisionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PppoeAltaPolicyTest {

    @Test
    fun `a fiber subscription is born as dynamic pppoe even outside vlan 100`() {
        val decision = PppoeAltaPolicy.decide(
            enabled = true,
            installationType = InstallationType.FIBER,
            vlan = "1"
        )

        assertEquals(AccessMode.PPPOE_DYNAMIC, decision.accessMode)
        assertEquals(PppoeProvisionStatus.PENDING, decision.provisionStatus)
        assertEquals(false, decision.needsStaticIp)
    }

    @Test
    fun `a fiber subscription is born as dynamic pppoe when the flag is off`() {
        val decision = PppoeAltaPolicy.decide(
            enabled = false,
            installationType = InstallationType.FIBER,
            vlan = "100"
        )

        assertEquals(AccessMode.PPPOE_DYNAMIC, decision.accessMode)
        assertEquals(PppoeProvisionStatus.PENDING, decision.provisionStatus)
        assertEquals(false, decision.needsStaticIp)
    }

    @Test
    fun `a missing vlan still becomes pppoe for fiber`() {
        listOf(null, "", "   ").forEach { vlan ->
            val decision = PppoeAltaPolicy.decide(
                enabled = true,
                installationType = InstallationType.FIBER,
                vlan = vlan
            )
            assertEquals(AccessMode.PPPOE_DYNAMIC, decision.accessMode, "vlan=$vlan")
            assertEquals(PppoeProvisionStatus.PENDING, decision.provisionStatus, "vlan=$vlan")
        }
    }

    @Test
    fun `wireless and tv only never migrate to pppoe`() {
        listOf(InstallationType.WIRELESS, InstallationType.ONLY_TV_FIBER).forEach { type ->
            val decision = PppoeAltaPolicy.decide(enabled = true, installationType = type, vlan = "100")
            assertEquals(AccessMode.STATIC_IP, decision.accessMode, "type=$type")
            assertNull(decision.provisionStatus, "type=$type")
            assertEquals(true, decision.needsStaticIp, "type=$type")
        }
    }

    @Test
    fun `a null installation type is treated as static`() {
        val decision = PppoeAltaPolicy.decide(enabled = true, installationType = null, vlan = "100")

        assertEquals(AccessMode.STATIC_IP, decision.accessMode)
        assertNull(decision.provisionStatus)
        assertEquals(true, decision.needsStaticIp)
    }

    @Test
    fun `vlan 100 fiber remains pppoe`() {
        val decision = PppoeAltaPolicy.decide(true, InstallationType.FIBER, " 100 ")

        assertEquals(AccessMode.PPPOE_DYNAMIC, decision.accessMode)
        assertEquals(false, decision.needsStaticIp)
    }
}
