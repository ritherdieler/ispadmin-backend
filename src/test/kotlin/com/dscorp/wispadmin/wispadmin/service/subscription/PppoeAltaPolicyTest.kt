package com.dscorp.wispadmin.wispadmin.service.subscription

import com.dscorp.wispadmin.wispadmin.data.model.AccessMode
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.PppoeProvisionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PppoeAltaPolicyTest {

    @Test
    fun `a fiber subscription on vlan 100 is born as dynamic pppoe when the flag is on`() {
        val decision = PppoeAltaPolicy.decide(
            enabled = true,
            installationType = InstallationType.FIBER,
            vlan = "100"
        )

        assertEquals(AccessMode.PPPOE_DYNAMIC, decision.accessMode)
        assertEquals(PppoeProvisionStatus.PENDING, decision.provisionStatus)
    }

    @Test
    fun `a fiber subscription outside vlan 100 falls back to static ip and never fails`() {
        val decision = PppoeAltaPolicy.decide(
            enabled = true,
            installationType = InstallationType.FIBER,
            vlan = "1"
        )

        assertEquals(AccessMode.STATIC_IP, decision.accessMode)
        assertEquals(PppoeProvisionStatus.SKIPPED_VLAN, decision.provisionStatus)
    }

    @Test
    fun `a missing vlan also falls back instead of blocking the sale`() {
        listOf(null, "", "   ").forEach { vlan ->
            val decision = PppoeAltaPolicy.decide(
                enabled = true,
                installationType = InstallationType.FIBER,
                vlan = vlan
            )
            assertEquals(AccessMode.STATIC_IP, decision.accessMode, "vlan=$vlan")
            assertEquals(PppoeProvisionStatus.SKIPPED_VLAN, decision.provisionStatus, "vlan=$vlan")
        }
    }

    @Test
    fun `with the flag off every subscription stays static and is not marked as skipped by vlan`() {
        val decision = PppoeAltaPolicy.decide(
            enabled = false,
            installationType = InstallationType.FIBER,
            vlan = "100"
        )

        assertEquals(AccessMode.STATIC_IP, decision.accessMode)
        assertEquals(PppoeProvisionStatus.SKIPPED_DISABLED, decision.provisionStatus)
    }

    @Test
    fun `wireless and tv only never migrate to pppoe`() {
        listOf(InstallationType.WIRELESS, InstallationType.ONLY_TV_FIBER).forEach { type ->
            val decision = PppoeAltaPolicy.decide(enabled = true, installationType = type, vlan = "100")
            assertEquals(AccessMode.STATIC_IP, decision.accessMode, "type=$type")
            assertNull(decision.provisionStatus, "type=$type")
        }
    }

    @Test
    fun `a null installation type is treated as static`() {
        val decision = PppoeAltaPolicy.decide(enabled = true, installationType = null, vlan = "100")

        assertEquals(AccessMode.STATIC_IP, decision.accessMode)
        assertNull(decision.provisionStatus)
    }

    @Test
    fun `only dynamic pppoe skips ip allocation`() {
        assertEquals(
            false,
            PppoeAltaPolicy.decide(true, InstallationType.FIBER, "100").needsStaticIp
        )
        assertEquals(
            true,
            PppoeAltaPolicy.decide(true, InstallationType.FIBER, "1").needsStaticIp
        )
        assertEquals(
            true,
            PppoeAltaPolicy.decide(false, InstallationType.FIBER, "100").needsStaticIp
        )
    }

    @Test
    fun `vlan is compared after trimming`() {
        val decision = PppoeAltaPolicy.decide(true, InstallationType.FIBER, " 100 ")

        assertEquals(AccessMode.PPPOE_DYNAMIC, decision.accessMode)
    }
}
