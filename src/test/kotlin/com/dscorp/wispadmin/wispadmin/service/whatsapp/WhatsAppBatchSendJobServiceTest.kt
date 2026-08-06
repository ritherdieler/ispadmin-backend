package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.dto.WhatsAppMessageBatchResultDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppMessageResultDto
import com.dscorp.wispadmin.wispadmin.service.WhatsAppBackofficeMessageService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppTemplateDeliveryService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class WhatsAppBatchSendJobServiceTest {

    private val messageService = mockk<WhatsAppBackofficeMessageService>()
    private val crmEventPublisher = mockk<CrmEventPublisher>(relaxed = true)
    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var jobService: WhatsAppBatchSendJobService

    @BeforeEach
    fun setUp() {
        jobService = WhatsAppBatchSendJobService(
            messageService = messageService,
            crmEventPublisher = crmEventPublisher,
            batchExecutor = executor
        )
    }

    @Test
    fun `enqueue returns accepted dto with campaignId and total`() {
        every {
            messageService.executeSendSelected(
                templateCode = any(),
                targetIds = any(),
                campaignId = any(),
                operatorUsername = any(),
                onEachResult = any()
            )
        } returns batchResult(2, sent = 2)

        val accepted = jobService.enqueueSendSelected(
            templateCode = "PAYMENT_REMINDER",
            targetIds = listOf(1, 2),
            operatorUsername = "admin"
        )

        assertNotNull(accepted.campaignId)
        assertEquals("PAYMENT_REMINDER", accepted.templateCode)
        assertEquals(2, accepted.total)
        assertEquals("RUNNING", accepted.status)
    }

    @Test
    fun `enqueue publishes BATCH_PROGRESS and completes status`() {
        val progressSlot = slot<(WhatsAppMessageResultDto) -> Unit>()
        val done = CountDownLatch(1)
        every {
            messageService.executeSendSelected(
                templateCode = any(),
                targetIds = any(),
                campaignId = any(),
                operatorUsername = any(),
                onEachResult = capture(progressSlot)
            )
        } answers {
            progressSlot.captured.invoke(
                WhatsAppMessageResultDto(
                    targetId = 1,
                    targetType = "PAYMENT",
                    paymentId = 1,
                    subscriptionId = 10,
                    phone = "987654321",
                    clientName = "Ana",
                    status = WhatsAppTemplateDeliveryService.STATUS_SENT,
                    reason = null
                )
            )
            batchResult(1, sent = 1).also { done.countDown() }
        }

        val accepted = jobService.enqueueSendSelected(
            templateCode = "PAYMENT_REMINDER",
            targetIds = listOf(1),
            operatorUsername = null
        )

        assertTrue(done.await(5, TimeUnit.SECONDS))

        verify {
            crmEventPublisher.publish(
                CrmEventPublisher.BATCH_PROGRESS,
                match {
                    it["campaignId"] == accepted.campaignId &&
                        it["processed"] == 1 &&
                        it["total"] == 1
                }
            )
        }

        val status = jobService.getBatchStatus(accepted.campaignId)
        assertNotNull(status)
        assertEquals("COMPLETED", status!!.status)
        assertEquals(1, status.sent)
    }

    private fun batchResult(total: Int, sent: Int) = WhatsAppMessageBatchResultDto(
        templateCode = "PAYMENT_REMINDER",
        requestedLimit = total,
        candidates = total,
        sent = sent,
        skipped = 0,
        failed = total - sent,
        details = emptyList()
    )
}
