package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppQuickReply
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppQuickReplyBody
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppQuickReplyDto
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppQuickReplyRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class WhatsAppQuickReplyService(
    private val repository: WhatsAppQuickReplyRepository
) {
    fun list(): List<WhatsAppQuickReplyDto> =
        repository.findAll()
            .sortedBy { it.shortcut.lowercase() }
            .map { it.toDto() }

    fun create(body: WhatsAppQuickReplyBody): WhatsAppQuickReplyDto {
        val normalized = normalize(body)
        if (repository.existsByShortcutIgnoreCase(normalized.shortcut)) {
            throw CrmConversationValidationException("El atajo ${normalized.shortcut} ya existe.")
        }
        val now = LocalDateTime.now()
        val saved = repository.save(
            WhatsAppQuickReply(
                title = normalized.title,
                shortcut = normalized.shortcut,
                content = normalized.content,
                createdAt = now,
                updatedAt = now
            )
        )
        return saved.toDto()
    }

    fun update(id: Long, body: WhatsAppQuickReplyBody): WhatsAppQuickReplyDto {
        val existing = repository.findById(id).orElse(null)
            ?: throw CrmConversationNotFoundException("Respuesta rapida no encontrada.")
        val normalized = normalize(body)
        if (repository.existsByShortcutIgnoreCaseAndIdNot(normalized.shortcut, id)) {
            throw CrmConversationValidationException("El atajo ${normalized.shortcut} ya existe.")
        }
        existing.title = normalized.title
        existing.shortcut = normalized.shortcut
        existing.content = normalized.content
        existing.updatedAt = LocalDateTime.now()
        return repository.save(existing).toDto()
    }

    fun delete(id: Long) {
        val existing = repository.findById(id).orElse(null)
            ?: throw CrmConversationNotFoundException("Respuesta rapida no encontrada.")
        repository.delete(existing)
    }

    private fun normalize(body: WhatsAppQuickReplyBody): NormalizedQuickReply {
        val title = body.title.trim()
        val shortcut = normalizeShortcut(body.shortcut)
        val content = body.content.trim()
        if (title.isBlank()) throw CrmConversationValidationException("El titulo es obligatorio.")
        if (shortcut.isBlank()) throw CrmConversationValidationException("El atajo es obligatorio.")
        if (content.isBlank()) throw CrmConversationValidationException("El contenido es obligatorio.")
        if (!shortcut.startsWith("/")) {
            throw CrmConversationValidationException("El atajo debe comenzar con /.")
        }
        if (title.length > 120) throw CrmConversationValidationException("Titulo demasiado largo.")
        if (shortcut.length > 64) throw CrmConversationValidationException("Atajo demasiado largo.")
        return NormalizedQuickReply(title, shortcut, content)
    }

    private fun normalizeShortcut(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ""
        return if (trimmed.startsWith("/")) trimmed else "/${trimmed.trimStart('/')}"
    }

    private data class NormalizedQuickReply(
        val title: String,
        val shortcut: String,
        val content: String
    )

    private fun WhatsAppQuickReply.toDto() = WhatsAppQuickReplyDto(
        id = id!!,
        title = title,
        shortcut = shortcut,
        content = content,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
