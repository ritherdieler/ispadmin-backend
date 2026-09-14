package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppAuditLog
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppAuditLogRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WhatsAppAuditServiceTest {

    private val repository = mockk<WhatsAppAuditLogRepository>()
    private lateinit var service: WhatsAppAuditService

    @BeforeEach
    fun setUp() {
        service = WhatsAppAuditService(repository)
        every { repository.save(any()) } answers { firstArg() }
    }

    @Test
    fun `registra ACCESS con operatorUsername sin secretos`() {
        val saved = slot<WhatsAppAuditLog>()
        every { repository.save(capture(saved)) } answers { firstArg() }

        service.recordAccess(
            operatorUsername = "sec1",
            resource = "/whatsapp/conversations",
            details = "search=ana"
        )

        assertEquals(WhatsAppAuditService.ACTION_ACCESS, saved.captured.action)
        assertEquals("sec1", saved.captured.operatorUsername)
        assertEquals("/whatsapp/conversations", saved.captured.resource)
        assertFalse(saved.captured.details.orEmpty().contains("Bearer", ignoreCase = true))
        assertFalse(saved.captured.details.orEmpty().contains("token", ignoreCase = true))
    }

    @Test
    fun `registra REPLY con telefono y longitud sin cuerpo completo sensible`() {
        val saved = slot<WhatsAppAuditLog>()
        every { repository.save(capture(saved)) } answers { firstArg() }

        service.recordReply(
            operatorUsername = "admin1",
            phone = "51902354183",
            textLength = 42
        )

        assertEquals(WhatsAppAuditService.ACTION_REPLY, saved.captured.action)
        assertEquals("51902354183", saved.captured.phone)
        assertTrue(saved.captured.details.orEmpty().contains("textLength=42"))
        assertFalse(saved.captured.details.orEmpty().contains("accessToken"))
    }

    @Test
    fun `registra MARK_READ`() {
        service.recordMarkRead(
            operatorUsername = "sec1",
            phone = "51902354183",
            inboundMessageId = 99
        )

        verify {
            repository.save(match {
                it.action == WhatsAppAuditService.ACTION_MARK_READ &&
                    it.operatorUsername == "sec1" &&
                    it.phone == "51902354183" &&
                    it.details?.contains("inboundMessageId=99") == true
            })
        }
    }
}
