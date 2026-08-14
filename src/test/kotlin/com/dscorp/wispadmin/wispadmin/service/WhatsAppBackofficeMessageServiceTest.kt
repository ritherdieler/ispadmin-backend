package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.data.model.Payment
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppSyncedTemplateRepository
import com.dscorp.wispadmin.wispadmin.service.whatsapp.UnpaidInvoiceAggregate
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateCode
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateDefinition
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateDeliveryService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WelcomeTemplateContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.reset
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class WhatsAppBackofficeMessageServiceTest {

    private val paymentRepository = mock(PaymentRepository::class.java)
    private val subscriptionRepository = mock(SubscriptionRepository::class.java)
    private val whatsAppMessageLogRepository = mock(WhatsAppMessageLogRepository::class.java)
    private val syncedTemplateRepository = mock(WhatsAppSyncedTemplateRepository::class.java)
    private val whatsAppProperties = WhatsAppProperties()
    private val templateDeliveryService = mock(WhatsAppTemplateDeliveryService::class.java)

    private lateinit var service: WhatsAppBackofficeMessageService
    private val paymentRowsById = mutableMapOf<Int, Array<Any>>()

    @BeforeEach
    fun setUp() {
        paymentRowsById.clear()
        reset(
            paymentRepository,
            subscriptionRepository,
            whatsAppMessageLogRepository,
            syncedTemplateRepository,
            templateDeliveryService
        )
        whatsAppProperties.backoffice.batchConcurrency = 8
        `when`(paymentRepository.findWhatsAppPaymentRowsByIds(ArgumentMatchers.anyList())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val ids = invocation.getArgument<List<Int>>(0)
            ids.mapNotNull { paymentRowsById[it] }
        }
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
    fun `sendSelected aggregates partial failures with clientName and keeps target order`() {
        stubPaymentRow(1, 10, "Ana", "Lopez", "987654321")
        stubPaymentRow(2, 11, "Bruno", "Diaz", "987654322")
        stubPaymentRow(3, 12, "Carla", "Ruiz", "987654323")

        stubHasSentToday { paymentId -> paymentId == 2 }

        doAnswer { invocation ->
            val paymentId = invocation.getArgument<Int?>(5)
            if (paymentId == 3) {
                throw RuntimeException("Meta API 500: boom")
            }
            WhatsAppMessageLog(
                paymentId = paymentId,
                subscriptionId = invocation.getArgument(6),
                phone = invocation.getArgument(2),
                messageType = "PAYMENT_REMINDER",
                message = "ok",
                status = WhatsAppTemplateDeliveryService.STATUS_SENT
            )
        }.`when`(templateDeliveryService).deliverTemplate(
            anyNonNull(WhatsAppTemplateDefinition::class.java),
            anyNonNull(Subscription::class.java),
            ArgumentMatchers.anyString(),
            nullableArg(Payment::class.java),
            nullableArg(Payment::class.java),
            nullableArg(Int::class.javaObjectType),
            nullableArg(Int::class.javaObjectType),
            nullableArg(WelcomeTemplateContext::class.java),
            nullableArg(String::class.java),
            nullableArg(String::class.java),
            nullableArg(UnpaidInvoiceAggregate::class.java)
        )

        val result = service.sendSelected(
            WhatsAppTemplateCode.PAYMENT_REMINDER.name,
            listOf(1, 2, 3),
            operatorUsername = "admin"
        )

        assertEquals(1, result.sent)
        assertEquals(1, result.skipped)
        assertEquals(1, result.failed)
        assertEquals(listOf(1, 2, 3), result.details.map { it.targetId })
        assertEquals("Ana Lopez", result.details[0].clientName)
        assertEquals(WhatsAppTemplateDeliveryService.STATUS_SENT, result.details[0].status)
        assertEquals("Bruno Diaz", result.details[1].clientName)
        assertEquals(WhatsAppTemplateDeliveryService.STATUS_SKIPPED, result.details[1].status)
        assertEquals("Carla Ruiz", result.details[2].clientName)
        assertEquals(WhatsAppTemplateDeliveryService.STATUS_FAILED, result.details[2].status)
    }

    @Test
    fun `sendSelected never throws when one deliverTemplate fails`() {
        stubPaymentRow(1, 10, "Ana", "Lopez", "987654321")
        stubPaymentRow(2, 11, "Bruno", "Diaz", "987654322")

        stubHasSentToday { false }

        doThrow(RuntimeException("Meta down"))
            .`when`(templateDeliveryService)
            .deliverTemplate(
                anyNonNull(WhatsAppTemplateDefinition::class.java),
                anyNonNull(Subscription::class.java),
                ArgumentMatchers.anyString(),
                nullableArg(Payment::class.java),
                nullableArg(Payment::class.java),
                nullableArg(Int::class.javaObjectType),
                nullableArg(Int::class.javaObjectType),
                nullableArg(WelcomeTemplateContext::class.java),
                nullableArg(String::class.java),
                nullableArg(String::class.java),
                nullableArg(UnpaidInvoiceAggregate::class.java)
            )

        val result = service.sendSelected(
            WhatsAppTemplateCode.PAYMENT_REMINDER.name,
            listOf(1, 2)
        )

        assertEquals(0, result.sent)
        assertEquals(2, result.failed)
        assertEquals(2, result.details.size)
        assertTrue(result.details.all { it.status == WhatsAppTemplateDeliveryService.STATUS_FAILED })
        assertTrue(result.details.all { !it.clientName.isNullOrBlank() })
    }

    @Test
    fun `sendSelected respects batch concurrency limit`() {
        whatsAppProperties.backoffice.batchConcurrency = 2
        service = WhatsAppBackofficeMessageService(
            paymentRepository = paymentRepository,
            subscriptionRepository = subscriptionRepository,
            whatsAppMessageLogRepository = whatsAppMessageLogRepository,
            syncedTemplateRepository = syncedTemplateRepository,
            whatsAppProperties = whatsAppProperties,
            templateDeliveryService = templateDeliveryService
        )

        (1..4).forEach { id ->
            stubPaymentRow(id, id + 100, "Client", "N$id", "98765432$id")
        }

        stubHasSentToday { false }

        val inFlight = AtomicInteger(0)
        val maxInFlight = AtomicInteger(0)
        val started = CountDownLatch(4)

        doAnswer { invocation ->
            val current = inFlight.incrementAndGet()
            maxInFlight.updateAndGet { maxOf(it, current) }
            started.countDown()
            Thread.sleep(80)
            inFlight.decrementAndGet()
            WhatsAppMessageLog(
                paymentId = invocation.getArgument(5),
                subscriptionId = invocation.getArgument(6),
                phone = invocation.getArgument(2),
                messageType = "PAYMENT_REMINDER",
                message = "ok",
                status = WhatsAppTemplateDeliveryService.STATUS_SENT
            )
        }.`when`(templateDeliveryService).deliverTemplate(
            anyNonNull(WhatsAppTemplateDefinition::class.java),
            anyNonNull(Subscription::class.java),
            ArgumentMatchers.anyString(),
            nullableArg(Payment::class.java),
            nullableArg(Payment::class.java),
            nullableArg(Int::class.javaObjectType),
            nullableArg(Int::class.javaObjectType),
            nullableArg(WelcomeTemplateContext::class.java),
            nullableArg(String::class.java),
            nullableArg(String::class.java),
            nullableArg(UnpaidInvoiceAggregate::class.java)
        )

        val result = service.sendSelected(
            WhatsAppTemplateCode.PAYMENT_REMINDER.name,
            listOf(1, 2, 3, 4)
        )

        assertTrue(started.await(5, TimeUnit.SECONDS))
        assertEquals(4, result.sent)
        assertTrue(maxInFlight.get() <= 2, "maxInFlight=${maxInFlight.get()}")
        verify(templateDeliveryService, times(4)).deliverTemplate(
            anyNonNull(WhatsAppTemplateDefinition::class.java),
            anyNonNull(Subscription::class.java),
            ArgumentMatchers.anyString(),
            nullableArg(Payment::class.java),
            nullableArg(Payment::class.java),
            nullableArg(Int::class.javaObjectType),
            nullableArg(Int::class.javaObjectType),
            nullableArg(WelcomeTemplateContext::class.java),
            nullableArg(String::class.java),
            nullableArg(String::class.java),
            nullableArg(UnpaidInvoiceAggregate::class.java)
        )
    }

    @Test
    fun `sendSelected prefetch payment rows in a single batch query`() {
        stubPaymentRow(1, 10, "Ana", "Lopez", "987654321")
        stubPaymentRow(2, 11, "Bruno", "Diaz", "987654322")
        stubHasSentToday { false }

        doAnswer { invocation ->
            WhatsAppMessageLog(
                paymentId = invocation.getArgument(5),
                subscriptionId = invocation.getArgument(6),
                phone = invocation.getArgument(2),
                messageType = "PAYMENT_REMINDER",
                message = "ok",
                status = WhatsAppTemplateDeliveryService.STATUS_SENT
            )
        }.`when`(templateDeliveryService).deliverTemplate(
            anyNonNull(WhatsAppTemplateDefinition::class.java),
            anyNonNull(Subscription::class.java),
            ArgumentMatchers.anyString(),
            nullableArg(Payment::class.java),
            nullableArg(Payment::class.java),
            nullableArg(Int::class.javaObjectType),
            nullableArg(Int::class.javaObjectType),
            nullableArg(WelcomeTemplateContext::class.java),
            nullableArg(String::class.java),
            nullableArg(String::class.java),
            nullableArg(UnpaidInvoiceAggregate::class.java)
        )

        service.sendSelected(
            WhatsAppTemplateCode.PAYMENT_REMINDER.name,
            listOf(1, 2)
        )

        verify(paymentRepository, times(1)).findWhatsAppPaymentRowsByIds(listOf(1, 2))
        verify(paymentRepository, never()).findWhatsAppPaymentRowById(ArgumentMatchers.anyInt())
    }

    @Test
    fun `listCandidates for reminder consolidates three unpaid invoices into one candidate`() {
        `when`(paymentRepository.findReminderCandidatePaymentRows(ArgumentMatchers.anyInt())).thenReturn(
            listOf(
                reminderAggregateRow(
                    oldestPaymentId = 101,
                    subscriptionId = 10,
                    phone = "987654321",
                    totalAmount = 240.0,
                    invoiceCount = 3,
                    periodFrom = LocalDateTime.of(2026, 7, 1, 0, 0),
                    periodTo = LocalDateTime.of(2026, 9, 1, 0, 0),
                )
            )
        )

        val response = service.listCandidates(WhatsAppTemplateCode.PAYMENT_REMINDER.name)
        val candidate = response.candidates.single()

        assertEquals(101, candidate.paymentId)
        assertEquals(101, candidate.targetId)
        assertEquals(10, candidate.subscriptionId)
        assertEquals(240.0, candidate.amount)
        assertEquals(3, candidate.invoiceCount)
        assertEquals("01/07/2026 - 01/09/2026", candidate.periodSummary)
        assertEquals("01/07/2026", candidate.billingDate)
        assertFalse(candidate.isBimonthly)
    }

    @Test
    fun `listCandidates for reminder maps isBimonthly and accumulated amount`() {
        `when`(paymentRepository.findReminderCandidatePaymentRows(ArgumentMatchers.anyInt())).thenReturn(
            listOf(
                reminderAggregateRow(
                    oldestPaymentId = 201,
                    subscriptionId = 20,
                    phone = "987654321",
                    totalAmount = 160.0,
                    invoiceCount = 2,
                    periodFrom = LocalDateTime.of(2026, 7, 1, 0, 0),
                    periodTo = LocalDateTime.of(2026, 8, 1, 0, 0),
                    isBimonthly = true,
                )
            )
        )

        val response = service.listCandidates(WhatsAppTemplateCode.PAYMENT_REMINDER.name)
        val candidate = response.candidates.single()

        assertTrue(candidate.isBimonthly)
        assertEquals(160.0, candidate.amount)
        assertEquals(2, candidate.invoiceCount)
    }

    @Test
    fun `listCandidates for reminder defaults isBimonthly when column is missing`() {
        `when`(paymentRepository.findReminderCandidatePaymentRows(ArgumentMatchers.anyInt())).thenReturn(
            listOf(reminderRow(paymentId = 1, subscriptionId = 10, phone = "987654321"))
        )

        val response = service.listCandidates(WhatsAppTemplateCode.PAYMENT_REMINDER.name)

        assertFalse(response.candidates.single().isBimonthly)
        assertEquals(1, response.candidates.single().invoiceCount)
    }

    @Test
    fun `listCandidates partitions valid and invalid phones for payment reminder`() {
        `when`(paymentRepository.findReminderCandidatePaymentRows(ArgumentMatchers.anyInt())).thenReturn(
            listOf(
                reminderRow(paymentId = 1, subscriptionId = 10, phone = "987654321"),
                reminderRow(paymentId = 2, subscriptionId = 11, phone = "12345"),
                reminderRow(paymentId = 3, subscriptionId = 12, phone = null),
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
    fun `listCandidates returns all rows up to the configured limit`() {
        val rows = (1..250).map { index ->
            reminderRow(
                paymentId = index,
                subscriptionId = index + 1000,
                phone = if (index % 2 == 0) "987654321" else "invalid",
            )
        }
        `when`(paymentRepository.findReminderCandidatePaymentRows(ArgumentMatchers.anyInt())).thenReturn(rows)

        val response = service.listCandidates(WhatsAppTemplateCode.PAYMENT_REMINDER.name)

        assertEquals(125, response.candidates.size)
        assertEquals(125, response.invalidPhones.size)
        assertEquals(250, response.totals.valid + response.totals.invalid)
    }

    @Test
    fun `listCandidates for reminder aplica limite alineado al daily-limit configurado`() {
        whatsAppProperties.messagingDailyLimitOverride = 2000
        `when`(paymentRepository.findReminderCandidatePaymentRows(ArgumentMatchers.anyInt())).thenReturn(emptyList())

        service.listCandidates(WhatsAppTemplateCode.PAYMENT_REMINDER.name)

        verify(paymentRepository, times(1)).findReminderCandidatePaymentRows(2000)
    }

    @Test
    fun `listCandidates for reminder consulta hasSentToday en una sola query batch para N candidatos`() {
        `when`(paymentRepository.findReminderCandidatePaymentRows(ArgumentMatchers.anyInt())).thenReturn(
            listOf(
                reminderRow(paymentId = 1, subscriptionId = 10, phone = "987654321"),
                reminderRow(paymentId = 2, subscriptionId = 11, phone = "987654322"),
                reminderRow(paymentId = 3, subscriptionId = 12, phone = "987654323"),
            )
        )
        `when`(
            whatsAppMessageLogRepository.findPaymentIdsSentToday(
                ArgumentMatchers.anyCollection(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                anyNonNull(LocalDateTime::class.java),
                anyNonNull(LocalDateTime::class.java)
            )
        ).thenReturn(setOf(2))

        val response = service.listCandidates(WhatsAppTemplateCode.PAYMENT_REMINDER.name)

        verify(whatsAppMessageLogRepository, times(1)).findPaymentIdsSentToday(
            ArgumentMatchers.anyCollection(),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString(),
            anyNonNull(LocalDateTime::class.java),
            anyNonNull(LocalDateTime::class.java)
        )
        verify(whatsAppMessageLogRepository, never()).existsByPaymentIdAndMessageTypeAndStatusAndCreatedAtBetween(
            ArgumentMatchers.anyInt(),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString(),
            anyNonNull(LocalDateTime::class.java),
            anyNonNull(LocalDateTime::class.java)
        )
        assertTrue(response.candidates.first { it.targetId == 2 }.alreadySentToday)
        assertFalse(response.candidates.first { it.targetId == 1 }.alreadySentToday)
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
    fun `listTemplates consulta plantillas sincronizadas en una sola query batch`() {
        service.listTemplates()

        verify(syncedTemplateRepository, times(1)).findByNameIn(ArgumentMatchers.anyCollection())
        verify(syncedTemplateRepository, never()).findByName(ArgumentMatchers.anyString())
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

    @Suppress("UNCHECKED_CAST")
    private fun reminderRow(paymentId: Int, subscriptionId: Int, phone: String?): Array<Any> {
        return arrayOf(
            paymentId,
            subscriptionId,
            "Juan",
            "Perez",
            phone,
            50.0,
            null,
            LocalDateTime.of(2026, 7, 1, 0, 0),
            null,
        ) as Array<Any>
    }

    @Suppress("UNCHECKED_CAST")
    private fun reminderAggregateRow(
        oldestPaymentId: Int,
        subscriptionId: Int,
        phone: String?,
        totalAmount: Double,
        invoiceCount: Int,
        periodFrom: LocalDateTime,
        periodTo: LocalDateTime,
        isBimonthly: Boolean = false,
    ): Array<Any> {
        return arrayOf(
            oldestPaymentId,
            subscriptionId,
            "Juan",
            "Perez",
            phone,
            totalAmount,
            null,
            periodFrom,
            null,
            invoiceCount,
            periodTo,
            isBimonthly,
        ) as Array<Any>
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

    private fun stubPaymentRow(
        paymentId: Int,
        subscriptionId: Int,
        firstName: String,
        lastName: String,
        phone: String,
        paid: Boolean = false
    ) {
        val row = paymentRowArray(paymentId, subscriptionId, firstName, lastName, phone, paid)
        paymentRowsById[paymentId] = row
        `when`(paymentRepository.findWhatsAppPaymentRowById(paymentId)).thenReturn(listOf(row))
    }

    private fun stubPaymentRowsBatch(rows: List<Array<Any>>) {
        val ids = rows.map { (it[0] as Number).toInt() }
        `when`(paymentRepository.findWhatsAppPaymentRowsByIds(ids)).thenReturn(rows)
    }

    private fun paymentRowArray(
        paymentId: Int,
        subscriptionId: Int,
        firstName: String,
        lastName: String,
        phone: String,
        paid: Boolean = false
    ): Array<Any> {
        @Suppress("UNCHECKED_CAST")
        return arrayOf(
            paymentId,
            subscriptionId,
            firstName,
            lastName,
            phone,
            50.0,
            null,
            LocalDateTime.of(2026, 7, 1, 0, 0),
            null,
            paid,
        ) as Array<Any>
    }

    private fun stubHasSentToday(predicate: (Int) -> Boolean) {
        `when`(
            whatsAppMessageLogRepository.existsByPaymentIdAndMessageTypeAndStatusAndCreatedAtBetween(
                ArgumentMatchers.anyInt(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                anyNonNull(LocalDateTime::class.java),
                anyNonNull(LocalDateTime::class.java)
            )
        ).thenAnswer { invocation ->
            predicate(invocation.getArgument(0))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyNonNull(type: Class<T>): T = ArgumentMatchers.any(type) as T

    private fun <T> nullableArg(type: Class<T>): T? = ArgumentMatchers.nullable(type)
}
