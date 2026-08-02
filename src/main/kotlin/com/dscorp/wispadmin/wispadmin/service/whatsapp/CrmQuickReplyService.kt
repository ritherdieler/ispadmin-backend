package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.CrmQuickReply
import com.dscorp.wispadmin.wispadmin.dto.CrmQuickReplyBody
import com.dscorp.wispadmin.wispadmin.dto.CrmQuickReplyDto
import com.dscorp.wispadmin.wispadmin.repository.CrmQuickReplyRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class CrmQuickReplyService(
    private val repository: CrmQuickReplyRepository
) {

    fun listForUser(userId: Int): List<CrmQuickReplyDto> =
        repository.findVisibleForUser(userId).map { it.toDto() }

    fun create(userId: Int, isAdmin: Boolean, body: CrmQuickReplyBody): CrmQuickReplyDto {
        val title = body.title.trim()
        val text = body.body.trim()
        if (title.isBlank()) throw CrmConversationValidationException("El titulo es obligatorio.")
        if (text.isBlank()) throw CrmConversationValidationException("El cuerpo es obligatorio.")
        if (title.length > 120) throw CrmConversationValidationException("Titulo demasiado largo.")
        if (text.length > 2000) throw CrmConversationValidationException("Cuerpo demasiado largo.")
        if (body.global && !isAdmin) {
            throw CrmConversationForbiddenException("Solo ADMIN puede crear respuestas globales.")
        }
        val now = LocalDateTime.now()
        val saved = repository.save(
            CrmQuickReply(
                title = title,
                body = text,
                ownerUserId = if (body.global) null else userId,
                createdAt = now,
                updatedAt = now
            )
        )
        return saved.toDto()
    }

    fun update(id: Long, userId: Int, isAdmin: Boolean, body: CrmQuickReplyBody): CrmQuickReplyDto {
        val existing = repository.findById(id).orElse(null)
            ?: throw CrmConversationNotFoundException("Respuesta rapida no encontrada.")
        assertCanEdit(existing, userId, isAdmin)
        val title = body.title.trim()
        val text = body.body.trim()
        if (title.isBlank()) throw CrmConversationValidationException("El titulo es obligatorio.")
        if (text.isBlank()) throw CrmConversationValidationException("El cuerpo es obligatorio.")
        if (body.global && !isAdmin && existing.ownerUserId != null) {
            throw CrmConversationForbiddenException("Solo ADMIN puede convertir a global.")
        }
        existing.title = title
        existing.body = text
        if (isAdmin) {
            existing.ownerUserId = if (body.global) null else (existing.ownerUserId ?: userId)
        }
        existing.updatedAt = LocalDateTime.now()
        return repository.save(existing).toDto()
    }

    fun delete(id: Long, userId: Int, isAdmin: Boolean) {
        val existing = repository.findById(id).orElse(null)
            ?: throw CrmConversationNotFoundException("Respuesta rapida no encontrada.")
        assertCanEdit(existing, userId, isAdmin)
        repository.delete(existing)
    }

    fun renderBody(template: String, clientName: String?, phone: String): String {
        return template
            .replace("{{clientName}}", clientName?.takeIf { it.isNotBlank() } ?: "cliente")
            .replace("{{phone}}", phone)
    }

    private fun assertCanEdit(reply: CrmQuickReply, userId: Int, isAdmin: Boolean) {
        if (isAdmin) return
        if (reply.ownerUserId == null) {
            throw CrmConversationForbiddenException("Solo ADMIN puede editar respuestas globales.")
        }
        if (reply.ownerUserId != userId) {
            throw CrmConversationForbiddenException("No puedes editar esta respuesta rapida.")
        }
    }

    private fun CrmQuickReply.toDto() = CrmQuickReplyDto(
        id = id!!,
        title = title,
        body = body,
        ownerUserId = ownerUserId,
        global = ownerUserId == null,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
