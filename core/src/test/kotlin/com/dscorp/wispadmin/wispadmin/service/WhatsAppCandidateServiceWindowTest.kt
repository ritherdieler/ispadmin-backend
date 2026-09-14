package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppSyncedTemplateRepository
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppServiceWindowService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateCode
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateDeliveryService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class WhatsAppCandidateServiceWindowTest {

    private val paymentRepository = mockk<PaymentRepository>(relaxed = true)
    private val subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true)
    private val whatsAppMessageLogRepository = mockk<WhatsAppMessageLogRepository>(relaxed = true)
    private val syncedTemplateRepository = mockk<WhatsAppSyncedTemplateRepository>(relaxed = true)
    private val serviceWindowService = mockk<WhatsAppServiceWindowService>()
    private val templateDeliveryService = mockk<WhatsAppTemplateDeliveryService>(relaxed = true)
    private val whatsAppProperties = WhatsAppProperties()

    private lateinit var service: WhatsAppBackofficeMessageService

    private val expiresAt: LocalDateTime = LocalDateTime.of(2026, 9, 2, 10, 0)

    @BeforeEach
    fun setUp() {
        service = WhatsAppBackofficeMessageService(
            paymentRepository = paymentRepository,
            subscriptionRepository = subscriptionRepository,
            whatsAppMessageLogRepository = whatsAppMessageLogRepository,
            syncedTemplateRepository = syncedTemplateRepository,
            whatsAppProperties = whatsAppProperties,
            templateDeliveryService = templateDeliveryService,
            serviceWindowService = serviceWindowService
        )
        every { whatsAppMessageLogRepository.findPaymentIdsSentToday(any(), any(), any(), any(), any()) } returns emptySet()
        every { serviceWindowService.getServiceWindows(any()) } returns emptyMap()
    }

    private fun paymentRow(paymentId: Int, subscriptionId: Int, phone: String): Array<Any> {
        @Suppress("UNCHECKED_CAST")
        return arrayOf(
            paymentId,
            subscriptionId,
            "Ana",
            "Lopez",
            phone,
            50.0,
            null,
            LocalDateTime.of(2026, 7, 1, 0, 0),
            null,
            1,
            LocalDateTime.of(2026, 7, 31, 0, 0),
            false
        ) as Array<Any>
    }

    private fun window(phone: String, open: Boolean, expiresAt: LocalDateTime?) =
        WhatsAppServiceWindowService.WhatsAppServiceWindowStatus(
            phone = phone,
            open = open,
            expiresAt = expiresAt
        )

    @Test
    fun `el listado de candidatos ya trae la ventana de servicio resuelta en lote`() {
        every { paymentRepository.findReminderCandidatePaymentRows(any()) } returns listOf(
            paymentRow(1, 10, "987654321"),
            paymentRow(2, 11, "987654322")
        )
        val phonesSlot = slot<Collection<String>>()
        every { serviceWindowService.getServiceWindows(capture(phonesSlot)) } returns mapOf(
            "987654321" to window("987654321", open = true, expiresAt = expiresAt)
        )

        val response = service.listCandidates(WhatsAppTemplateCode.PAYMENT_REMINDER.name)

        verify(exactly = 1) { serviceWindowService.getServiceWindows(any()) }
        assertTrue(phonesSlot.captured.contains("987654321"))
        assertTrue(phonesSlot.captured.contains("987654322"))

        val first = response.candidates.first { it.paymentId == 1 }
        assertTrue(first.serviceWindowOpen)
        assertEquals(expiresAt, first.serviceWindowExpiresAt)

        val second = response.candidates.first { it.paymentId == 2 }
        assertFalse(second.serviceWindowOpen)
        assertNull(second.serviceWindowExpiresAt)
    }

    @Test
    fun `reconoce la ventana registrada con la variante internacional del numero`() {
        every { paymentRepository.findReminderCandidatePaymentRows(any()) } returns listOf(
            paymentRow(1, 10, "987654321")
        )
        every { serviceWindowService.getServiceWindows(any()) } returns mapOf(
            "51987654321" to window("51987654321", open = true, expiresAt = expiresAt)
        )

        val response = service.listCandidates(WhatsAppTemplateCode.PAYMENT_REMINDER.name)

        assertTrue(response.candidates.single().serviceWindowOpen)
        assertEquals(expiresAt, response.candidates.single().serviceWindowExpiresAt)
    }

    @Test
    fun `sin candidatos validos no consulta ventanas de servicio`() {
        every { paymentRepository.findReminderCandidatePaymentRows(any()) } returns listOf(
            paymentRow(1, 10, "123")
        )

        val response = service.listCandidates(WhatsAppTemplateCode.PAYMENT_REMINDER.name)

        assertTrue(response.candidates.isEmpty())
        assertEquals(1, response.invalidPhones.size)
        verify(exactly = 0) { serviceWindowService.getServiceWindows(any()) }
    }
}
