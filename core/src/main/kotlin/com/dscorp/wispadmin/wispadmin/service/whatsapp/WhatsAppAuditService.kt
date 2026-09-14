package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppAuditLog
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppAuditLogRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class WhatsAppAuditService(
    private val repository: WhatsAppAuditLogRepository
) {

    fun recordAccess(operatorUsername: String?, resource: String, details: String? = null) {
        save(
            action = ACTION_ACCESS,
            operatorUsername = operatorUsername,
            phone = null,
            resource = resource,
            details = sanitizeDetails(details)
        )
    }

    fun recordReply(operatorUsername: String?, phone: String, textLength: Int) {
        save(
            action = ACTION_REPLY,
            operatorUsername = operatorUsername,
            phone = phone,
            resource = "/whatsapp/conversations/$phone/reply",
            details = "textLength=$textLength"
        )
    }

    fun recordBotTrace(
        phone: String?,
        intent: String,
        source: String,
        confidence: Double,
        escalateReason: String? = null
    ) {
        val details = buildString {
            append("intent=").append(intent)
            append(";source=").append(source)
            append(";confidence=").append("%.2f".format(confidence))
            if (!escalateReason.isNullOrBlank()) {
                append(";escalate=").append(escalateReason)
            }
        }
        save(
            action = ACTION_BOT_TRACE,
            operatorUsername = "bot",
            phone = phone,
            resource = "/crm/bot/trace",
            details = details
        )
    }

    fun recordMarkRead(operatorUsername: String?, phone: String?, inboundMessageId: Int?) {
        val details = buildString {
            if (inboundMessageId != null) append("inboundMessageId=$inboundMessageId")
            if (phone != null) {
                if (isNotEmpty()) append(';')
                append("phone=$phone")
            }
        }.ifBlank { null }
        save(
            action = ACTION_MARK_READ,
            operatorUsername = operatorUsername,
            phone = phone,
            resource = if (inboundMessageId != null) {
                "/whatsapp/conversations/$inboundMessageId/mark-read"
            } else {
                "/whatsapp/conversations/${phone.orEmpty()}/mark-all-read"
            },
            details = details
        )
    }

    private fun save(
        action: String,
        operatorUsername: String?,
        phone: String?,
        resource: String?,
        details: String?
    ) {
        repository.save(
            WhatsAppAuditLog(
                action = action,
                operatorUsername = operatorUsername,
                phone = phone,
                resource = resource,
                details = details,
                createdAt = LocalDateTime.now()
            )
        )
    }

    private fun sanitizeDetails(details: String?): String? {
        if (details.isNullOrBlank()) return null
        val redacted = SECRET_PATTERNS.fold(details) { acc, regex ->
            regex.replace(acc, REDACTED)
        }
        return redacted.take(1000)
    }

    companion object {
        const val ACTION_ACCESS = "ACCESS"
        const val ACTION_REPLY = "REPLY"
        const val ACTION_MARK_READ = "MARK_READ"
        const val ACTION_BOT_TRACE = "BOT_TRACE"
        private const val REDACTED = "[REDACTED]"
        private val SECRET_PATTERNS = listOf(
            Regex("""(?i)bearer\s+[A-Za-z0-9\-._~+/]+=*"""),
            Regex("""(?i)(access[_-]?token|refresh[_-]?token|app[_-]?secret|authorization)\s*[:=]\s*\S+"""),
            Regex("""(?i)(api[_-]?key|openai|sk-[A-Za-z0-9\-._]+)\s*[:=]?\s*\S+""")
        )
    }
}
