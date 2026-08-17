package com.dscorp.wispadmin.wispadmin.data.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SubscriptionFiberOnuSnDtoTest {

    @Test
    fun `toDto maps fiberOnu serial to fiberOnuSn`() {
        val subscription = Subscription(
            id = 42,
            firstName = "Ana",
            lastName = "Perez",
            equipmentCondition = EquipmentCondition.LOAN,
            fiberOnu = Onu(sn = "HWTC15F5CD86")
        )

        val dto = subscription.toDto()

        assertEquals("HWTC15F5CD86", dto.fiberOnuSn)
        assertEquals(true, dto.hasFiberOnu)
    }

    @Test
    fun `toDto leaves fiberOnuSn null when subscription has no ONU`() {
        val subscription = Subscription(
            id = 7,
            equipmentCondition = EquipmentCondition.SOLD
        )

        val dto = subscription.toDto()

        assertNull(dto.fiberOnuSn)
        assertEquals(false, dto.hasFiberOnu)
    }
}
