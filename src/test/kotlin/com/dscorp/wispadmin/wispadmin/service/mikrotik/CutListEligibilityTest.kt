package com.dscorp.wispadmin.wispadmin.service.mikrotik

import com.dscorp.wispadmin.wispadmin.data.model.AccessMode
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CutListEligibilityTest {

    @Test
    fun `static ip subscription is cut by its address`() {
        val subscription = subscription(AccessMode.STATIC_IP, ip = "192.168.30.10")

        assertEquals("192.168.30.10", CutListEligibility.cutListIp(subscription))
    }

    @Test
    fun `pppoe fixed subscription is still cut by its address`() {
        val subscription = subscription(AccessMode.PPPOE_FIXED, ip = "192.168.26.108")

        assertEquals("192.168.26.108", CutListEligibility.cutListIp(subscription))
    }

    @Test
    fun `pppoe dynamic subscription is never cut by address list`() {
        val subscription = subscription(AccessMode.PPPOE_DYNAMIC, ip = null)

        assertNull(CutListEligibility.cutListIp(subscription))
    }

    @Test
    fun `pppoe dynamic is not cut by address even if a stale ip is stored`() {
        val subscription = subscription(AccessMode.PPPOE_DYNAMIC, ip = "10.64.0.55")

        assertNull(CutListEligibility.cutListIp(subscription))
    }

    @Test
    fun `static subscription without ip yields no address to cut`() {
        assertNull(CutListEligibility.cutListIp(subscription(AccessMode.STATIC_IP, ip = null)))
        assertNull(CutListEligibility.cutListIp(subscription(AccessMode.STATIC_IP, ip = "")))
        assertNull(CutListEligibility.cutListIp(subscription(AccessMode.STATIC_IP, ip = "   ")))
    }

    @Test
    fun `malformed ip is rejected`() {
        assertNull(CutListEligibility.cutListIp(subscription(AccessMode.STATIC_IP, ip = "no-es-ip")))
        assertNull(CutListEligibility.cutListIp(subscription(AccessMode.STATIC_IP, ip = "192.168.30")))
    }

    @Test
    fun `ip is trimmed before being used`() {
        assertEquals(
            "192.168.30.10",
            CutListEligibility.cutListIp(subscription(AccessMode.STATIC_IP, ip = " 192.168.30.10 "))
        )
    }

    private fun subscription(accessMode: AccessMode, ip: String?) = Subscription(
        id = 1,
        firstName = "Cliente",
        lastName = "Prueba",
        dni = "00000001",
        equipmentCondition = EquipmentCondition.LOAN,
        serviceStatus = ServiceStatus.ACTIVE
    ).apply {
        this.accessMode = accessMode
        this.ip = ip
    }
}
