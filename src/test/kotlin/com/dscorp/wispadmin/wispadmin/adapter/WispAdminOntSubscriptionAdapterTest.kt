package com.dscorp.wispadmin.wispadmin.adapter

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.Onu
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WispAdminOntSubscriptionAdapterTest {

    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val adapter = WispAdminOntSubscriptionAdapter(subscriptionRepository)

    @Test
    fun `findActiveByOnuSn retorna abonado activo por sn exacto`() {
        val onu = Onu(sn = "HWTC00000016")
        val subscription = Subscription(
            id = 555,
            firstName = "Juan",
            lastName = "Perez",
            serviceStatus = ServiceStatus.ACTIVE,
            equipmentCondition = EquipmentCondition.LOAN,
            fiberOnu = onu
        )
        every { subscriptionRepository.findActiveByFiberOnuSn("HWTC00000016", "00000016") } returns listOf(subscription)

        val info = adapter.findActiveByOnuSn("HWTC00000016")

        assertEquals(555, info?.subscriptionId)
        assertEquals("Juan Perez", info?.customerName)
        assertEquals("ACTIVE", info?.serviceStatus)
    }

    @Test
    fun `findActiveByOnuSn sin coincidencias retorna null`() {
        every { subscriptionRepository.findActiveByFiberOnuSn("HWTC99999999", "99999999") } returns emptyList()

        assertNull(adapter.findActiveByOnuSn("HWTC99999999"))
    }

    @Test
    fun `findActiveByOnuSn usa nombre comercial para cliente business`() {
        val onu = Onu(sn = "HWTC00000001")
        val subscription = Subscription(
            id = 10,
            clientType = Subscription.ClientType.BUSINESS,
            businessName = "Acme ISP SAC",
            serviceStatus = ServiceStatus.ACTIVE,
            equipmentCondition = EquipmentCondition.LOAN,
            fiberOnu = onu
        )
        every { subscriptionRepository.findActiveByFiberOnuSn("HWTC00000001", "00000001") } returns listOf(subscription)

        val info = adapter.findActiveByOnuSn("HWTC00000001")

        assertEquals("Acme ISP SAC", info?.customerName)
    }
}
