package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.Payment
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppSyncedTemplateRepository
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateCode
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateDeliveryService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.time.LocalDateTime

class WhatsAppBackofficeMessageServiceTest {

    private val paymentRepository = mock(PaymentRepository::class.java)
    private val subscriptionRepository = mock(SubscriptionRepository::class.java)
    private val whatsAppMessageLogRepository = mock(WhatsAppMessageLogRepository::class.java)
    private val syncedTemplateRepository = mock(WhatsAppSyncedTemplateRepository::class.java)
    private val whatsAppProperties = WhatsAppProperties()
    private val templateDeliveryService = mock(WhatsAppTemplateDeliveryService::class.java)

    private lateinit var service: WhatsAppBackofficeMessageService

    @BeforeEach
    fun setUp() {
        service = WhatsAppBackofficeMessageService(
            paymentRepository = paymentRepository,
            subscriptionRepository = subscriptionRepository,
            whatsAppMessageLogRepository = whatsAppMessageLogRepository,
            syncedTemplateRepository = syncedTemplateRepository,
            whatsAppProperties = whatsAppProperties,
            templateDeliveryService = templateDeliveryService
        )
    }

    @Test
    fun `listCandidates keeps one payment reminder row per subscription`() {
        val sharedSubscription = Subscription(
            firstName = "Mari",
            lastName = "Rimac",
            phone = "987654321",
            equipmentCondition = EquipmentCondition.LOAN,
        ).apply { id = 10 }

        `when`(paymentRepository.findAllReminderCandidatePayments()).thenReturn(
            listOf(
                Payment(
                    discountAmount = 0.0,
                    paid = false,
                    amountToPay = 50.0,
                    billingDateDatetime = LocalDateTime.of(2026, 5, 1, 0, 0),
                    subscription = sharedSubscription,
                ).apply { id = 1 },
                Payment(
                    discountAmount = 0.0,
                    paid = false,
                    amountToPay = 60.0,
                    billingDateDatetime = LocalDateTime.of(2026, 6, 1, 0, 0),
                    subscription = sharedSubscription,
                ).apply { id = 2 },
            )
        )

        val response = service.listCandidates(WhatsAppTemplateCode.PAYMENT_REMINDER.name)

        assertEquals(1, response.candidates.size)
        assertEquals(1, response.totals.valid)
        assertEquals(10, response.candidates.first().subscriptionId)
        assertEquals(1, response.candidates.first().paymentId)
    }

    @Test
    fun `listCandidates partitions valid and invalid phones for payment reminder`() {
        `when`(paymentRepository.findAllReminderCandidatePayments()).thenReturn(
            listOf(
                reminderPayment(id = 1, subscriptionId = 10, phone = "987654321"),
                reminderPayment(id = 2, subscriptionId = 11, phone = "12345"),
                reminderPayment(id = 3, subscriptionId = 12, phone = null),
            )
        )

        val response = service.listCandidates(WhatsAppTemplateCode.PAYMENT_REMINDER.name)

        assertEquals(1, response.candidates.size)
        assertEquals(2, response.invalidPhones.size)
        assertEquals(1, response.totals.valid)
        assertEquals(2, response.totals.invalid)
        assertEquals("987654321", response.candidates.first().phone)
        assertEquals(11, response.invalidPhones.first().subscriptionId)
        assertTrue(response.invalidPhones.all { it.targetType == "PAYMENT" })
    }

    @Test
    fun `listCandidates returns all rows without limit`() {
        val payments = (1..250).map { index ->
            reminderPayment(
                id = index,
                subscriptionId = index + 1000,
                phone = if (index % 2 == 0) "987654321" else "invalid",
            )
        }
        `when`(paymentRepository.findAllReminderCandidatePayments()).thenReturn(payments)

        val response = service.listCandidates(WhatsAppTemplateCode.PAYMENT_REMINDER.name)

        assertEquals(125, response.candidates.size)
        assertEquals(125, response.invalidPhones.size)
        assertEquals(250, response.totals.valid + response.totals.invalid)
    }

    @Test
    fun `listCandidates rejects service cut notice template`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.listCandidates(WhatsAppTemplateCode.SERVICE_CUT_NOTICE.name)
        }
    }

    @Test
    fun `listCandidates rejects welcome customer template`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.listCandidates(WhatsAppTemplateCode.WELCOME_CUSTOMER.name)
        }
    }

    @Test
    fun `listTemplates marks manual send only for payment reminder and validation`() {
        val templates = service.listTemplates()

        val reminder = templates.first { it.code == WhatsAppTemplateCode.PAYMENT_REMINDER.name }
        val validation = templates.first { it.code == WhatsAppTemplateCode.PAYMENT_VALIDATION.name }
        val cutNotice = templates.first { it.code == WhatsAppTemplateCode.SERVICE_CUT_NOTICE.name }
        val welcome = templates.first { it.code == WhatsAppTemplateCode.WELCOME_CUSTOMER.name }

        assertTrue(reminder.manualSendEnabled)
        assertTrue(validation.manualSendEnabled)
        assertFalse(cutNotice.manualSendEnabled)
        assertFalse(welcome.manualSendEnabled)
    }

    @Test
    fun `listCandidates partitions validation rows with invalid phones`() {
        val since = java.time.LocalDate.now()
            .minusDays(whatsAppProperties.backoffice.validationPaidDays.toLong())
            .atStartOfDay()
        `when`(paymentRepository.findAllValidationCandidatePaymentRows(since)).thenReturn(
            listOf(
                validationRow(paymentId = 1, subscriptionId = 10, phone = "987654321"),
                validationRow(paymentId = 2, subscriptionId = 11, phone = ""),
            )
        )

        val response = service.listCandidates(WhatsAppTemplateCode.PAYMENT_VALIDATION.name)

        assertEquals(1, response.candidates.size)
        assertEquals(1, response.invalidPhones.size)
        assertEquals(1, response.totals.valid)
        assertEquals(1, response.totals.invalid)
    }

    private fun reminderPayment(id: Int, subscriptionId: Int, phone: String?): Payment {
        val subscription = Subscription(
            firstName = "Juan",
            lastName = "Perez",
            phone = phone,
            equipmentCondition = EquipmentCondition.LOAN,
        ).apply { this.id = subscriptionId }

        return Payment(
            discountAmount = 0.0,
            paid = false,
            amountToPay = 50.0,
            billingDateDatetime = LocalDateTime.of(2026, 7, 1, 0, 0),
            subscription = subscription,
        ).apply { this.id = id }
    }

    private fun validationRow(paymentId: Int, subscriptionId: Int, phone: String): Array<Any> {
        return arrayOf(
            paymentId,
            subscriptionId,
            "Ana",
            "Lopez",
            phone,
            50.0,
            50.0,
            LocalDateTime.of(2026, 7, 10, 0, 0),
            LocalDateTime.of(2026, 7, 20, 0, 0),
        )
    }
}
