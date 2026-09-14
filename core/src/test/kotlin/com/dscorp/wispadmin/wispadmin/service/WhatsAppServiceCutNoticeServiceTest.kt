package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateCatalog
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateCode
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateDeliveryService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.LocalDateTime

class WhatsAppServiceCutNoticeServiceTest {

    private val paymentRepository = mock(PaymentRepository::class.java)
    private val whatsAppMessageLogRepository = mock(WhatsAppMessageLogRepository::class.java)
    private val templateDeliveryService = mock(WhatsAppTemplateDeliveryService::class.java)
    private val whatsAppProperties = WhatsAppProperties().apply {
        apiVersion = "v22.0"
        phoneNumberId = "123"
        businessAccountId = "456"
        accessToken = "token"
    }

    private var deliverTemplateInvocations = 0
    private lateinit var service: WhatsAppServiceCutNoticeService
    private lateinit var todayStart: LocalDateTime
    private lateinit var tomorrowStart: LocalDateTime

    @BeforeEach
    fun setUp() {
        deliverTemplateInvocations = 0
        todayStart = java.time.LocalDate.now().atStartOfDay()
        tomorrowStart = todayStart.plusDays(1)

        doAnswer {
            deliverTemplateInvocations++
            null
        }.`when`(templateDeliveryService).deliverTemplate(
            anyNonNull(),
            anyNonNull(),
            anyNonNull(),
            anyNullable(),
            anyNullable(),
            anyNullable(),
            anyNullable(),
            anyNullable(),
            anyNullable(),
            anyNullable(),
            anyNullable()
        )

        service = WhatsAppServiceCutNoticeService(
            paymentRepository = paymentRepository,
            whatsAppMessageLogRepository = whatsAppMessageLogRepository,
            templateDeliveryService = templateDeliveryService,
            whatsAppProperties = whatsAppProperties
        )

        stubNotSentToday(1)
        stubNotSentToday(2)
    }

    @Test
    fun `does nothing when whatsapp is not configured`() {
        whatsAppProperties.accessToken = ""

        service.sendCutNoticesForCandidates(listOf(subscription(1, "987654321")))

        assertEquals(0, deliverTemplateInvocations)
    }

    @Test
    fun `sends cut notice to all valid candidates with unpaid invoice`() {
        val subscriptions = listOf(
            subscription(1, "987654321"),
            subscription(2, "912345678"),
        )
        `when`(paymentRepository.findOldestUnpaidPaymentRow(1)).thenReturn(listOf(unpaidRow(10, 80.0)))
        `when`(paymentRepository.findOldestUnpaidPaymentRow(2)).thenReturn(listOf(unpaidRow(11, 60.0)))

        service.sendCutNoticesForCandidates(subscriptions)

        assertEquals(2, deliverTemplateInvocations)
    }

    @Test
    fun `skips invalid phone and continues with remaining candidates`() {
        `when`(paymentRepository.findOldestUnpaidPaymentRow(1)).thenReturn(listOf(unpaidRow(10, 80.0)))

        service.sendCutNoticesForCandidates(
            listOf(
                subscription(1, "987654321"),
                subscription(2, "12345"),
            )
        )

        assertEquals(1, deliverTemplateInvocations)
        val definition = WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.SERVICE_CUT_NOTICE)
        verify(templateDeliveryService).persistLog(
            null,
            2,
            "12345",
            definition.messageType,
            "El telefono debe ser un celular peruano valido.",
            WhatsAppTemplateDeliveryService.STATUS_SKIPPED,
            "El telefono debe ser un celular peruano valido."
        )
    }

    @Test
    fun `skips when cut notice already sent today`() {
        stubSentToday(1)

        service.sendCutNoticesForCandidates(listOf(subscription(1, "987654321")))

        assertEquals(0, deliverTemplateInvocations)
        verify(paymentRepository, never()).findOldestUnpaidPaymentRow(1)
    }

    @Test
    fun `skips when subscription has no unpaid invoice`() {
        `when`(paymentRepository.findOldestUnpaidPaymentRow(1)).thenReturn(emptyList())

        service.sendCutNoticesForCandidates(listOf(subscription(1, "987654321")))

        assertEquals(0, deliverTemplateInvocations)
        val definition = WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.SERVICE_CUT_NOTICE)
        verify(templateDeliveryService).persistLog(
            null,
            1,
            "987654321",
            definition.messageType,
            "El cliente no tiene facturas pendientes.",
            WhatsAppTemplateDeliveryService.STATUS_SKIPPED,
            "El cliente no tiene facturas pendientes."
        )
    }

    private fun stubNotSentToday(subscriptionId: Int) {
        `when`(
            whatsAppMessageLogRepository.existsBySubscriptionIdAndMessageTypeAndStatusAndCreatedAtBetween(
                subscriptionId,
                "SERVICE_CUT_NOTICE",
                "SENT",
                todayStart,
                tomorrowStart
            )
        ).thenReturn(false)
    }

    private fun stubSentToday(subscriptionId: Int) {
        `when`(
            whatsAppMessageLogRepository.existsBySubscriptionIdAndMessageTypeAndStatusAndCreatedAtBetween(
                subscriptionId,
                "SERVICE_CUT_NOTICE",
                "SENT",
                todayStart,
                tomorrowStart
            )
        ).thenReturn(true)
    }

    private fun subscription(id: Int, phone: String): Subscription {
        return Subscription(
            firstName = "Juan",
            lastName = "Perez",
            phone = phone,
            installationType = InstallationType.FIBER,
            equipmentCondition = EquipmentCondition.LOAN,
        ).apply { this.id = id }
    }

    private fun unpaidRow(paymentId: Int, amount: Double): Array<Any> {
        return arrayOf(paymentId, amount, LocalDateTime.of(2026, 6, 1, 0, 0))
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyNonNull(): T = org.mockito.ArgumentMatchers.any() as T

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyNullable(): T = org.mockito.ArgumentMatchers.any() as T
}
