package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class WelcomeVariableMapperTest {

    @Test
    fun `builds tv welcome context`() {
        val subscription = subscription(
            installationType = InstallationType.ONLY_TV_FIBER,
            planName = "TV Full HD",
            planPrice = 30.0,
            subscriptionPrice = 30.0,
            subscriptionDatetime = LocalDateTime.of(2026, 7, 15, 10, 0)
        )

        val context = WelcomeVariableMapper.buildContext(subscription)

        assertEquals("TV Cable", context.serviceTitle)
        assertEquals("Full HD + SD y mas de 90 canales", context.serviceDetails)
        assertEquals("TV Full HD", context.planName)
        assertEquals("30.00", context.planPrice)
        assertEquals("15", context.paymentDay)
        assertEquals(WelcomeServiceCopy.PAYMENT_INFO, context.paymentInfo)
    }

    @Test
    fun `builds fiber welcome context with plan speed`() {
        val subscription = subscription(
            installationType = InstallationType.FIBER,
            planName = "F200",
            planPrice = 80.0,
            subscriptionPrice = 80.0,
            downloadSpeed = 200000,
            uploadSpeed = 200000,
            subscriptionDatetime = LocalDateTime.of(2026, 7, 5, 10, 0)
        )

        val context = WelcomeVariableMapper.buildContext(subscription)

        assertEquals("Internet 100% Fibra Optica", context.serviceTitle)
        assertEquals("200 Mbps de bajada y 200 Mbps de subida", context.serviceDetails)
        assertEquals("F200", context.planName)
        assertEquals("80.00", context.planPrice)
        assertEquals("5", context.paymentDay)
    }

    @Test
    fun `builds wireless welcome context`() {
        val subscription = subscription(
            installationType = InstallationType.WIRELESS,
            planName = "Dedicado 50M",
            planPrice = 150.0,
            subscriptionPrice = null,
            subscriptionDatetime = null
        )

        val context = WelcomeVariableMapper.buildContext(subscription)

        assertEquals("Enlace Dedicado Inalambrico", context.serviceTitle)
        assertEquals("Alta disponibilidad para tu ubicacion", context.serviceDetails)
        assertEquals("Dedicado 50M", context.planName)
        assertEquals("150.00", context.planPrice)
        assertEquals("1", context.paymentDay)
    }

    @Test
    fun `welcome subscription row mapper maps extended query`() {
        val row = arrayOf<Any>(
            42,
            "Juan",
            "Perez",
            "987654321",
            "ACTIVE",
            "FIBER",
            79.9,
            LocalDateTime.of(2026, 7, 10, 8, 0),
            "F50",
            79.9,
            50000,
            50000
        )

        val subscription = WhatsAppSubscriptionRowMapper.welcomeSubscriptionFromRow(row)

        assertEquals(42, subscription.id)
        assertEquals(InstallationType.FIBER, subscription.installationType)
        assertEquals("F50", subscription.plan?.name)
        assertEquals(50000, subscription.plan?.downloadSpeed)
        assertEquals(LocalDateTime.of(2026, 7, 10, 8, 0), subscription.subscriptionDatetime)
    }

    private fun subscription(
        installationType: InstallationType,
        planName: String,
        planPrice: Double,
        subscriptionPrice: Double?,
        downloadSpeed: Int? = null,
        uploadSpeed: Int? = null,
        subscriptionDatetime: LocalDateTime? = LocalDateTime.of(2026, 7, 1, 0, 0)
    ): Subscription {
        return Subscription(
            firstName = "Juan",
            lastName = "Perez",
            phone = "987654321",
            price = subscriptionPrice,
            subscriptionDatetime = subscriptionDatetime,
            installationType = installationType,
            plan = Plan(
                id = 1,
                name = planName,
                price = planPrice,
                downloadSpeed = downloadSpeed,
                uploadSpeed = uploadSpeed,
                type = installationType
            ),
            equipmentCondition = EquipmentCondition.LOAN
        ).apply { id = 10 }
    }
}
