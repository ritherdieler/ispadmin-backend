package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppSyncedTemplate
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppSyncedTemplateRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WhatsAppTemplateDisplayServiceTest {

    private val syncedTemplateRepository = mockk<WhatsAppSyncedTemplateRepository>()
    private val templateSyncService = mockk<WhatsAppTemplateSyncService>(relaxed = true)
    private val whatsAppProperties = WhatsAppProperties().apply {
        apiVersion = "v21.0"
        accessToken = "token"
        phoneNumberId = "123"
        businessAccountId = "456"
    }

    private lateinit var service: WhatsAppTemplateDisplayService

    @BeforeEach
    fun setUp() {
        service = WhatsAppTemplateDisplayService(
            syncedTemplateRepository = syncedTemplateRepository,
            templateSyncService = templateSyncService,
            whatsAppProperties = whatsAppProperties
        )
        every { syncedTemplateRepository.findByName(any()) } returns null
        every { syncedTemplateRepository.findByNameIn(any()) } returns emptyList()
        every { syncedTemplateRepository.findAll() } returns emptyList()
    }

    @Test
    fun `buildLogPreview uses fallback when body is unavailable`() {
        val definition = WhatsAppTemplateCatalog.get(WhatsAppTemplateCode.PAYMENT_REMINDER)
        val parameters = listOf(
            NamedTemplateParameter("customer_name", "Ana"),
            NamedTemplateParameter("amount", "50.0"),
            NamedTemplateParameter("billing_period", "01/08/2026")
        )

        val preview = service.buildLogPreview(definition, parameters)

        assertEquals(
            "Estimado(a) Ana, le recordamos su pago pendiente de S/ 50.0 correspondiente al periodo 01/08/2026.",
            preview
        )
        verify(exactly = 1) { templateSyncService.syncFromMeta() }
    }

    @Test
    fun `resolveBodyText loads body from repository on each call`() {
        every { syncedTemplateRepository.findByName("payment_reminder_gigaperu") } returns WhatsAppSyncedTemplate(
            metaTemplateId = "1",
            name = "payment_reminder_gigaperu",
            bodyText = "Hola {{customer_name}}"
        )

        repeat(5) {
            assertEquals("Hola {{customer_name}}", service.resolveBodyText("payment_reminder_gigaperu"))
        }

        verify(exactly = 5) { syncedTemplateRepository.findByName("payment_reminder_gigaperu") }
    }

    @Test
    fun `displayStoredMessages resolves legacy templates without N plus one`() {
        every {
            syncedTemplateRepository.findByNameIn(match { it.contains("payment_reminder_gigaperu") })
        } returns listOf(
            WhatsAppSyncedTemplate(
                metaTemplateId = "1",
                name = "payment_reminder_gigaperu",
                bodyText = "Hola {{customer_name}}, pague {{amount}}."
            )
        )

        val messages = listOf(
            "payment_reminder_gigaperu [customer_name=Ana, amount=50.0]" to "PAYMENT_REMINDER",
            "payment_reminder_gigaperu [customer_name=Luis, amount=40.0]" to "PAYMENT_REMINDER",
            "Hola libre" to "AUTO_REPLY"
        )

        val result = service.displayStoredMessages(messages)

        assertEquals(
            listOf(
                "Hola Ana, pague 50.0.",
                "Hola Luis, pague 40.0.",
                "Hola libre"
            ),
            result
        )
        verify(exactly = 1) { syncedTemplateRepository.findByNameIn(any()) }
        verify(exactly = 0) { syncedTemplateRepository.findByName(any()) }
    }
}
