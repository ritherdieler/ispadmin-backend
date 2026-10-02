package com.dscorp.wispadmin.wispadmin.data.model

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.hibernate.Hibernate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertFalse

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

    @Test
    fun `subscription dto does not expose host router credentials`() {
        every { Hibernate.isInitialized(any()) } returns false
        val router = NetworkDevice(
            id = 8,
            name = "MK2",
            password = "router-secret-test",
            username = "router-user-test",
        )
        val additionalDevices = listOf(router)
        every { Hibernate.isInitialized(additionalDevices) } returns true
        val subscription = Subscription(
            id = 100,
            hostDevice = router,
            additionalDevices = additionalDevices,
            equipmentCondition = EquipmentCondition.LOAN,
        )

        val dto = subscription.toDto()
        val cutDto = subscription.toCutDto()
        val ipPoolDto = IpPool(id = 5, hostDevice = router).toDto()
        val serialized = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().writeValueAsString(dto)

        assertEquals(8, dto.hostDevice?.id)
        assertEquals(null, dto.hostDevice?.password)
        assertEquals(null, dto.hostDevice?.username)
        assertEquals(null, dto.additionalDevices?.single()?.password)
        assertEquals(null, cutDto.hostDevice?.password)
        assertEquals(null, ipPoolDto.hostDevice.password)
        assertFalse(serialized.contains("router-secret-test"))
        assertFalse(serialized.contains("router-user-test"))
    }
}
