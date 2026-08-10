package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.repository.WhatsAppQuickReplyRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class WhatsAppQuickReplySeedRunnerTest {

    @Test
    fun `seeds defaults only when table is empty`() {
        val repository = mockk<WhatsAppQuickReplyRepository>()
        every { repository.count() } returns 0
        every { repository.existsByShortcutIgnoreCase(any()) } returns false
        every { repository.save(any()) } answers { firstArg() }

        WhatsAppQuickReplySeedRunner(repository).run(null)

        verify(exactly = WhatsAppQuickReplyCatalog.defaults.size) { repository.save(any()) }
    }

    @Test
    fun `skips seeding when table already has rows`() {
        val repository = mockk<WhatsAppQuickReplyRepository>()
        every { repository.count() } returns 3

        WhatsAppQuickReplySeedRunner(repository).run(null)

        verify(exactly = 0) { repository.save(any()) }
    }
}
