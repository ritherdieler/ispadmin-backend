package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppQuickReply
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppQuickReplyRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class WhatsAppQuickReplySeedRunner(
    private val repository: WhatsAppQuickReplyRepository
) : ApplicationRunner {
    override fun run(args: ApplicationArguments?) {
        if (repository.count() > 0) return

        val now = LocalDateTime.now()
        WhatsAppQuickReplyCatalog.defaults.forEach { seed ->
            if (!repository.existsByShortcutIgnoreCase(seed.shortcut)) {
                repository.save(
                    WhatsAppQuickReply(
                        title = seed.title,
                        shortcut = seed.shortcut,
                        content = seed.content,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
        }
    }
}
