package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.config.WhatsAppWelcomeOnRegistrationProperties
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateCatalog
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateCode
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateDeliveryService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

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
    fun `does nothing when welcome on registration is disabled`() {
        whatsAppProperties.welcomeOnRegistration.enabled = false

        service.sendWelcomeIfApplicable(42)

        verifyNoInteractions(whatsAppMessageLogRepository, subscriptionRepository, templateDeliveryService)
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
            listOf(subscriptionRow(subscriptionId = 42, phone = "123456789"))
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
    fun `delivers welcome when subscription has valid phone and no prior sent log`() {
        val definition = WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.WELCOME_CUSTOMER)
        `when`(
            whatsAppMessageLogRepository.existsBySubscriptionIdAndMessageTypeAndStatus(
                42,
                definition.messageType,
                WhatsAppTemplateDeliveryService.STATUS_SENT
            )
        ).thenReturn(false)
        `when`(subscriptionRepository.findWhatsAppSubscriptionRowById(42)).thenReturn(
            listOf(subscriptionRow(subscriptionId = 42, phone = "987654321"))
        )

        service.sendWelcomeIfApplicable(42)

        assertEquals(1, deliverTemplateInvocations)
    }

    private fun subscriptionRow(subscriptionId: Int, phone: String): Array<Any> {
        return arrayOf(
            subscriptionId,
            "Juan",
            "Perez",
            phone,
            ServiceStatus.ACTIVE.name
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyNonNull(): T = org.mockito.ArgumentMatchers.any() as T

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyNullable(): T = org.mockito.ArgumentMatchers.any() as T
}
