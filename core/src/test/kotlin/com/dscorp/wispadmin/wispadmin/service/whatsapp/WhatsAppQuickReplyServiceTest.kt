package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppQuickReply
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppQuickReplyBody
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppQuickReplyRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional

class WhatsAppQuickReplyServiceTest {

    private val repository = mockk<WhatsAppQuickReplyRepository>()
    private lateinit var service: WhatsAppQuickReplyService

    @BeforeEach
    fun setUp() {
        service = WhatsAppQuickReplyService(repository)
    }

    @Test
    fun `list returns sorted quick replies`() {
        every { repository.findAll() } returns listOf(
            WhatsAppQuickReply(id = 2, title = "B", shortcut = "/b", content = "B"),
            WhatsAppQuickReply(id = 1, title = "A", shortcut = "/a", content = "A"),
        )

        val items = service.list()

        assertEquals("/a", items.first().shortcut)
        assertEquals("/b", items.last().shortcut)
    }

    @Test
    fun `create normalizes shortcut and persists`() {
        every { repository.existsByShortcutIgnoreCase("/saludo") } returns false
        every { repository.save(any()) } answers {
            firstArg<WhatsAppQuickReply>().copy(id = 10)
        }

        val dto = service.create(
            WhatsAppQuickReplyBody(title = "Saludo", shortcut = "saludo", content = "Hola")
        )

        assertEquals(10, dto.id)
        assertEquals("/saludo", dto.shortcut)
        assertEquals("Hola", dto.content)
    }

    @Test
    fun `create rejects duplicate shortcut`() {
        every { repository.existsByShortcutIgnoreCase("/saludo") } returns true

        assertThrows(CrmConversationValidationException::class.java) {
            service.create(WhatsAppQuickReplyBody(title = "Saludo", shortcut = "/saludo", content = "Hola"))
        }
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `update replaces fields`() {
        val existing = WhatsAppQuickReply(id = 3, title = "Old", shortcut = "/old", content = "Old body")
        every { repository.findById(3) } returns Optional.of(existing)
        every { repository.existsByShortcutIgnoreCaseAndIdNot("/nuevo", 3) } returns false
        every { repository.save(any()) } answers { firstArg() }

        val dto = service.update(
            3,
            WhatsAppQuickReplyBody(title = "Nuevo", shortcut = "/nuevo", content = "Nuevo body")
        )

        assertEquals("Nuevo", dto.title)
        assertEquals("/nuevo", dto.shortcut)
        assertEquals("Nuevo body", dto.content)
    }

    @Test
    fun `delete removes entity`() {
        val existing = WhatsAppQuickReply(id = 4, title = "X", shortcut = "/x", content = "Y")
        every { repository.findById(4) } returns Optional.of(existing)
        every { repository.delete(existing) } returns Unit

        service.delete(4)

        verify(exactly = 1) { repository.delete(existing) }
    }
}
