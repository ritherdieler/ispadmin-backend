package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppPhoneSession
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppPhoneSessionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class WhatsAppServiceWindowServiceBatchTest {

    private val phoneSessionRepository = mockk<WhatsAppPhoneSessionRepository>()
    private lateinit var service: WhatsAppServiceWindowService

    @BeforeEach
    fun setUp() {
        service = WhatsAppServiceWindowService(phoneSessionRepository)
    }

    @Test
    fun `getServiceWindows carga sesiones en batch sin N+1`() {
        val now = LocalDateTime.now()
        every { phoneSessionRepository.findAllById(any<Iterable<String>>()) } returns listOf(
            WhatsAppPhoneSession(
                phone = "51911111111",
                serviceWindowExpiresAt = now.plusHours(10),
                updatedAt = now.minusHours(1)
            )
        )

        val result = service.getServiceWindows(listOf("51911111111", "51922222222"))

        assertEquals(2, result.size)
        assertTrue(result.getValue("51911111111").open)
        assertFalse(result.getValue("51922222222").open)
        verify(exactly = 1) { phoneSessionRepository.findAllById(any<Iterable<String>>()) }
        verify(exactly = 0) { phoneSessionRepository.findById(any()) }
    }
}
