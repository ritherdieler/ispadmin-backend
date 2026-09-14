package com.dscorp.wispadmin.wispadmin.service.mikrotik

import com.dscorp.wispadmin.wispadmin.data.model.AccessMode
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SimpleQueueTargetTest {

    @Test
    fun `static ip subscription keeps its simple queue target`() {
        val subscription = subscription(AccessMode.STATIC_IP, ip = "192.168.30.10")

        assertEquals("192.168.30.10", SimpleQueueTarget.of(subscription))
        assertEquals("192.168.30.10/32", SimpleQueueTarget.printFilter(subscription))
        assertTrue(SimpleQueueTarget.isManaged(subscription))
    }

    @Test
    fun `pppoe fixed subscription keeps its simple queue target`() {
        val subscription = subscription(AccessMode.PPPOE_FIXED, ip = "192.168.26.108")

        assertEquals("192.168.26.108", SimpleQueueTarget.of(subscription))
        assertTrue(SimpleQueueTarget.isManaged(subscription))
    }

    @Test
    fun `pppoe dynamic subscription has no simple queue because the profile limits it`() {
        val subscription = subscription(AccessMode.PPPOE_DYNAMIC, ip = null)

        assertNull(SimpleQueueTarget.of(subscription))
        assertNull(SimpleQueueTarget.printFilter(subscription))
        assertFalse(SimpleQueueTarget.isManaged(subscription))
    }

    @Test
    fun `pppoe dynamic never builds a target even with a stale session ip`() {
        val subscription = subscription(AccessMode.PPPOE_DYNAMIC, ip = "10.64.0.55")

        assertNull(SimpleQueueTarget.of(subscription))
    }

    @Test
    fun `a missing or malformed ip never produces the literal null target`() {
        listOf(null, "", "   ", "null", "no-es-ip").forEach { ip ->
            val subscription = subscription(AccessMode.STATIC_IP, ip = ip)
            assertNull(SimpleQueueTarget.of(subscription), "ip=$ip")
            assertNull(SimpleQueueTarget.printFilter(subscription), "ip=$ip")
        }
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
