package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
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
}
