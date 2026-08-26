package com.dscorp.wispadmin.wispadmin.data.model

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.hibernate.Hibernate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SubscriptionToDtoLazySafetyTest {

    @BeforeEach
    fun setUp() {
        mockkStatic(Hibernate::class)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Hibernate::class)
    }

    @Test
    fun `toDto does not fail when payments collection is not initialized`() {
        every { Hibernate.isInitialized(any()) } returns false

        val subscription = Subscription(
            id = 99,
            firstName = "A",
            lastName = "B",
            dni = "12345678",
            password = "12345678",
            address = "x",
            phone = "900000000",
            subscriptionDatetime = java.time.LocalDateTime.now(),
            plan = null,
            place = null,
            location = null,
            technician = null,
            hostDevice = null,
            installationType = InstallationType.FIBER,
            equipmentCondition = EquipmentCondition.LOAN,
        )

        val dto = subscription.toDto()
        assertEquals(0, dto.pendingInvoiceQuantity)
        assertEquals(0.0, dto.totalDebt)
        assertEquals(0, dto.antiquityInMonths)
        assertEquals(0, dto.qualification)
    }
}
