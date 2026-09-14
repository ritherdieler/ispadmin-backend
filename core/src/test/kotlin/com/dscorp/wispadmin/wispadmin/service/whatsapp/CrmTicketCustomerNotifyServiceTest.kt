package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicket
import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicketStatus
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.service.WhatsAppSendResult
import com.dscorp.wispadmin.wispadmin.service.WhatsAppService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class CrmTicketCustomerNotifyServiceTest {

    private val whatsAppService = mockk<WhatsAppService>()
    private val serviceWindowService = mockk<WhatsAppServiceWindowService>()
    private val messageLogRepository = mockk<WhatsAppMessageLogRepository>()

    private lateinit var service: CrmTicketCustomerNotifyService

    @BeforeEach
    fun setUp() {
        service = CrmTicketCustomerNotifyService(
            whatsAppService = whatsAppService,
            serviceWindowService = serviceWindowService,
            messageLogRepository = messageLogRepository
        )
        every { messageLogRepository.save(any()) } answers { firstArg() }
    }

    @Test
    fun `notifyStatusChange sends text when service window is open`() {
        every { serviceWindowService.getServiceWindow("999111222") } returns
            WhatsAppServiceWindowService.WhatsAppServiceWindowStatus(
                phone = "999111222",
                open = true,
                expiresAt = LocalDateTime.now().plusHours(12)
            )
        every { whatsAppService.sendTextMessage(any(), any()) } returns
            WhatsAppSendResult(
                success = true,
                metaResponse = "{}",
                metaMessageId = "wamid.1",
                recipient = "999111222",
                senderPhoneNumberId = "1"
            )

        val sent = service.notifyStatusChange(
            ticket = AssistanceTicket(
                id = 9,
                phone = "999111222",
                category = "Sin Conexión a Internet",
                description = "luz roja",
                status = AssistanceTicketStatus.ASSIGNED
            ),
            oldStatus = "PENDING",
            newStatus = "ASSIGNED",
            assignedTo = "Juan Perez"
        )

        assertTrue(sent)
        verify { whatsAppService.sendTextMessage("999111222", match { it.contains("#9") && it.contains("Asignado") }) }
        verify { messageLogRepository.save(any<WhatsAppMessageLog>()) }
    }

    @Test
    fun `notifyStatusChange skips when service window closed without approved template`() {
        every { serviceWindowService.getServiceWindow("999111222") } returns
            WhatsAppServiceWindowService.WhatsAppServiceWindowStatus(
                phone = "999111222",
                open = false,
                expiresAt = null
            )

        val sent = service.notifyStatusChange(
            ticket = AssistanceTicket(
                id = 9,
                phone = "999111222",
                category = "Sin Conexión a Internet",
                description = "luz roja",
                status = AssistanceTicketStatus.ASSIGNED
            ),
            oldStatus = "PENDING",
            newStatus = "ASSIGNED",
            assignedTo = null
        )

        assertFalse(sent)
        verify(exactly = 0) { whatsAppService.sendTextMessage(any(), any()) }
    }
}
