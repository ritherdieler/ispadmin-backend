package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.config.WhatsAppWelcomeOnRegistrationProperties
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateCatalog
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateCode
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateDeliveryService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WelcomeTemplateContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import java.time.LocalDateTime

class WhatsAppWelcomeRegistrationServiceTest {

    private val subscriptionRepository = mock(SubscriptionRepository::class.java)
    private val whatsAppMessageLogRepository = mock(WhatsAppMessageLogRepository::class.java)
    private val templateDeliveryService = mock(WhatsAppTemplateDeliveryService::class.java)
    private val whatsAppProperties = WhatsAppProperties().apply {
        welcomeOnRegistration = WhatsAppWelcomeOnRegistrationProperties().apply { enabled = true }
    }

    private var deliverTemplateInvocations = 0

    private lateinit var service: WhatsAppWelcomeRegistrationService

    @BeforeEach
    fun setUp() {
        deliverTemplateInvocations = 0
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
            anyNullable()
        )

        service = WhatsAppWelcomeRegistrationService(
            subscriptionRepository = subscriptionRepository,
            whatsAppMessageLogRepository = whatsAppMessageLogRepository,
            templateDeliveryService = templateDeliveryService,
            whatsAppProperties = whatsAppProperties
        )
    }

    @Test
    fun `persists skipped log when welcome on registration is disabled`() {
        whatsAppProperties.welcomeOnRegistration.enabled = false

        val result = service.sendWelcomeAndGetResult(42)

        assertEquals(WhatsAppWelcomeRegistrationService.OUTCOME_DISABLED, result.outcome)
        verify(subscriptionRepository, never()).findWhatsAppSubscriptionRowById(42)
        verify(templateDeliveryService).persistLog(
            org.mockito.ArgumentMatchers.eq(null),
            org.mockito.ArgumentMatchers.eq(42),
            org.mockito.ArgumentMatchers.eq(""),
            org.mockito.ArgumentMatchers.eq("WELCOME_CUSTOMER"),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.eq(WhatsAppTemplateDeliveryService.STATUS_SKIPPED),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.eq(null),
            org.mockito.ArgumentMatchers.eq(null),
            org.mockito.ArgumentMatchers.eq(null)
        )
    }

    @Test
    fun `skips when welcome was already sent for subscription`() {
        val definition = WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.WELCOME_CUSTOMER)
        `when`(
            whatsAppMessageLogRepository.existsBySubscriptionIdAndMessageTypeAndStatus(
                42,
                definition.messageType,
                WhatsAppTemplateDeliveryService.STATUS_SENT
            )
        ).thenReturn(true)

        service.sendWelcomeIfApplicable(42)

        verify(subscriptionRepository, never()).findWhatsAppSubscriptionRowById(42)
        assertEquals(0, deliverTemplateInvocations)
    }

    @Test
    fun `skips and logs when phone is invalid`() {
        val definition = WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.WELCOME_CUSTOMER)
        `when`(
            whatsAppMessageLogRepository.existsBySubscriptionIdAndMessageTypeAndStatus(
                42,
                definition.messageType,
                WhatsAppTemplateDeliveryService.STATUS_SENT
            )
        ).thenReturn(false)
        `when`(subscriptionRepository.findWhatsAppSubscriptionRowById(42)).thenReturn(
            listOf(subscriptionRow(subscriptionId = 42, phone = "123456789", installationType = "FIBER"))
        )

        service.sendWelcomeIfApplicable(42)

        verify(templateDeliveryService).persistLog(
            null,
            42,
            "123456789",
            definition.messageType,
            "El telefono debe ser un celular peruano valido.",
            WhatsAppTemplateDeliveryService.STATUS_SKIPPED,
            "El telefono debe ser un celular peruano valido."
        )
        assertEquals(0, deliverTemplateInvocations)
    }

    @Test
    fun `delivers fiber welcome when subscription has valid phone and no prior sent log`() {
        val definition = WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.WELCOME_CUSTOMER)
        `when`(
            whatsAppMessageLogRepository.existsBySubscriptionIdAndMessageTypeAndStatus(
                42,
                definition.messageType,
                WhatsAppTemplateDeliveryService.STATUS_SENT
            )
        ).thenReturn(false)
        `when`(subscriptionRepository.findWhatsAppSubscriptionRowById(42)).thenReturn(
            listOf(
                subscriptionRow(
                    subscriptionId = 42,
                    phone = "987654321",
                    installationType = "FIBER",
                    planName = "F200",
                    planPrice = 80.0,
                    downloadSpeed = 200000,
                    uploadSpeed = 200000
                )
            )
        )

        service.sendWelcomeIfApplicable(42)

        assertEquals(1, deliverTemplateInvocations)

        val welcomeContextCaptor = ArgumentCaptor.forClass(WelcomeTemplateContext::class.java)
        verify(templateDeliveryService).deliverTemplate(
            anyNonNull(),
            anyNonNull(),
            anyNonNull(),
            anyNullable(),
            anyNullable(),
            anyNullable(),
            org.mockito.ArgumentMatchers.eq(42),
            welcomeContextCaptor.capture(),
            anyNullable(),
            anyNullable()
        )

        val welcomeContext = welcomeContextCaptor.value
        assertEquals("Internet 100% Fibra Optica", welcomeContext.serviceTitle)
        assertEquals("200 Mbps de bajada y 200 Mbps de subida", welcomeContext.serviceDetails)
        assertEquals("F200", welcomeContext.planName)
        assertEquals("80.00", welcomeContext.planPrice)
    }

    @Test
    fun `delivers tv welcome context for only tv fiber`() {
        val definition = WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.WELCOME_CUSTOMER)
        `when`(
            whatsAppMessageLogRepository.existsBySubscriptionIdAndMessageTypeAndStatus(
                43,
                definition.messageType,
                WhatsAppTemplateDeliveryService.STATUS_SENT
            )
        ).thenReturn(false)
        `when`(subscriptionRepository.findWhatsAppSubscriptionRowById(43)).thenReturn(
            listOf(
                subscriptionRow(
                    subscriptionId = 43,
                    phone = "987654321",
                    installationType = "ONLY_TV_FIBER",
                    planName = "TV Full HD",
                    planPrice = 30.0
                )
            )
        )

        service.sendWelcomeIfApplicable(43)

        val welcomeContextCaptor = ArgumentCaptor.forClass(WelcomeTemplateContext::class.java)
        verify(templateDeliveryService).deliverTemplate(
            anyNonNull(),
            anyNonNull(),
            anyNonNull(),
            anyNullable(),
            anyNullable(),
            anyNullable(),
            org.mockito.ArgumentMatchers.eq(43),
            welcomeContextCaptor.capture(),
            anyNullable(),
            anyNullable()
        )

        val welcomeContext = welcomeContextCaptor.value
        assertEquals("TV Cable", welcomeContext.serviceTitle)
        assertEquals("Full HD + SD y mas de 90 canales", welcomeContext.serviceDetails)
        assertEquals("TV Full HD", welcomeContext.planName)
        assertEquals("30.00", welcomeContext.planPrice)
    }

    @Test
    fun `delivers wireless welcome context`() {
        val definition = WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.WELCOME_CUSTOMER)
        `when`(
            whatsAppMessageLogRepository.existsBySubscriptionIdAndMessageTypeAndStatus(
                44,
                definition.messageType,
                WhatsAppTemplateDeliveryService.STATUS_SENT
            )
        ).thenReturn(false)
        `when`(subscriptionRepository.findWhatsAppSubscriptionRowById(44)).thenReturn(
            listOf(
                subscriptionRow(
                    subscriptionId = 44,
                    phone = "987654321",
                    installationType = "WIRELESS",
                    planName = "Dedicado 50M",
                    planPrice = 150.0
                )
            )
        )

        service.sendWelcomeIfApplicable(44)

        val welcomeContextCaptor = ArgumentCaptor.forClass(WelcomeTemplateContext::class.java)
        verify(templateDeliveryService).deliverTemplate(
            anyNonNull(),
            anyNonNull(),
            anyNonNull(),
            anyNullable(),
            anyNullable(),
            anyNullable(),
            org.mockito.ArgumentMatchers.eq(44),
            welcomeContextCaptor.capture(),
            anyNullable(),
            anyNullable()
        )

        val welcomeContext = welcomeContextCaptor.value
        assertEquals("Enlace Dedicado Inalambrico", welcomeContext.serviceTitle)
        assertEquals("Alta disponibilidad para tu ubicacion", welcomeContext.serviceDetails)
        assertEquals("Dedicado 50M", welcomeContext.planName)
        assertEquals("150.00", welcomeContext.planPrice)
    }

    private fun subscriptionRow(
        subscriptionId: Int,
        phone: String,
        installationType: String = "FIBER",
        planName: String = "F50",
        planPrice: Double = 50.0,
        subscriptionPrice: Double = planPrice,
        downloadSpeed: Int = 50000,
        uploadSpeed: Int = 50000
    ): Array<Any> {
        return arrayOf(
            subscriptionId,
            "Juan",
            "Perez",
            phone,
            ServiceStatus.ACTIVE.name,
            installationType,
            subscriptionPrice,
            LocalDateTime.of(2026, 7, 15, 10, 0),
            planName,
            planPrice,
            downloadSpeed,
            uploadSpeed
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyNonNull(): T = org.mockito.ArgumentMatchers.any() as T

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyNullable(): T = org.mockito.ArgumentMatchers.any() as T
}
