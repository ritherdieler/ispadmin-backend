package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.repository.WhatsAppSyncedTemplateRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WhatsAppTemplateSyncServiceResolveIdsTest {

    private val metaAnalyticsClient = mockk<WhatsAppMetaAnalyticsClient>()
    private val syncedTemplateRepository = mockk<WhatsAppSyncedTemplateRepository>()
    private val service = WhatsAppTemplateSyncService(metaAnalyticsClient, syncedTemplateRepository)

    @Test
    fun `resolveMetaTemplateIdsForAnalytics matches meta name case insensitively`() {
        every { syncedTemplateRepository.findAllByOrderByNameAsc() } returns listOf(
            com.dscorp.wispadmin.wispadmin.data.model.WhatsAppSyncedTemplate(
                metaTemplateId = "123",
                name = "payment_reminder_gigaperu",
                syncedAt = java.time.LocalDateTime.now()
            )
        )

        val ids = service.resolveMetaTemplateIdsForAnalytics(listOf("payment_reminder_gigaperu"))

        assertEquals(listOf("123"), ids)
    }

    @Test
    fun `resolveMetaTemplateIdsForAnalytics syncs from Meta when cache is empty`() {
        every { syncedTemplateRepository.findAllByOrderByNameAsc() } returnsMany listOf(
            emptyList(),
            listOf(
                com.dscorp.wispadmin.wispadmin.data.model.WhatsAppSyncedTemplate(
                    metaTemplateId = "456",
                    name = "payment_reminder_gigaperu",
                    syncedAt = java.time.LocalDateTime.now()
                )
            )
        )
        every { metaAnalyticsClient.fetchMessageTemplates() } returns ObjectMapper().readTree(
            """
            {
              "data": [{
                "id": "456",
                "name": "payment_reminder_gigaperu",
                "status": "APPROVED",
                "components": [{"type": "BODY", "text": "Hola"}]
              }]
            }
            """.trimIndent()
        )
        every { syncedTemplateRepository.findById("456") } returns java.util.Optional.empty()
        every { syncedTemplateRepository.saveAll(any<List<com.dscorp.wispadmin.wispadmin.data.model.WhatsAppSyncedTemplate>>()) } answers {
            firstArg()
        }

        val ids = service.resolveMetaTemplateIdsForAnalytics(listOf("payment_reminder_gigaperu"))

        assertEquals(listOf("456"), ids)
        verify(exactly = 1) { metaAnalyticsClient.fetchMessageTemplates() }
    }
}
