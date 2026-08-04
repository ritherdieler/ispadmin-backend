package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.dto.WhatsAppCampaignSummaryDto
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WhatsAppCsvExportServiceTest {

    private val service = WhatsAppCsvExportService()

    @Test
    fun `exportCampaigns formats createdAt as a readable date instead of leaking raw ISO string`() {
        val campaign = WhatsAppCampaignSummaryDto(
            id = "camp-1",
            templateCode = "PAYMENT_REMINDER",
            templateLabel = "Recordatorio de pago",
            sent = 10,
            delivered = 8,
            read = 5,
            failed = 1,
            responded = 2,
            paid = 1,
            operatorName = "operator.a",
            createdAt = "2026-08-04T10:15:30",
            conversionAmount = 50.0
        )

        val csv = service.exportCampaigns(listOf(campaign))
        val dataRow = csv.lines()[1]

        assertTrue(dataRow.contains("2026-08-04 10:15:30"))
    }
}
