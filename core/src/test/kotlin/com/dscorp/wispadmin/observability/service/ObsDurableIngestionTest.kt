package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.wispadmin.observability.ReportedEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import com.dscorp.wispadmin.observability.entity.ObsDeliveryReceipt
import com.dscorp.wispadmin.observability.repository.ObsDeliveryReceiptRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.util.Optional

class ObsDurableIngestionTest {
    @Test fun `replayed delivery after lost HTTP response is ingested once`() {
        val ingestion = mockk<ObsIngestionService>()
        every { ingestion.persistEvent(any()) } returns 7
        val receipts = mockk<ObsDeliveryReceiptRepository>()
        val stored = mutableMapOf<String, ObsDeliveryReceipt>()
        every { receipts.findById(any()) } answers { Optional.ofNullable(stored[firstArg<String>()]) }
        every { receipts.saveAndFlush(any()) } answers { firstArg<ObsDeliveryReceipt>().also { stored[it.id] = it } }
        every { receipts.save(any()) } answers { firstArg() }
        val json = jacksonObjectMapper()
        val service = ObsDurableIngestionService(ingestion, receipts, json)
        val event = ReportedEvent("error", "backend", "error", "Falló gestión", "OMCI_FAILED", null,
            environment = "staging", correlationId = "operation-1")
        ObsDurableIngestionService(ingestion, receipts, json).persist("operation-1:17", event)
        service.persist("operation-1:17", event)
        verify(exactly = 1) { ingestion.persistEvent(event) }
    }
}
