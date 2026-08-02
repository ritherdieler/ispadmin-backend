package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppPhoneSession
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppPhoneSessionRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class WhatsAppServiceWindowService(
    private val phoneSessionRepository: WhatsAppPhoneSessionRepository
) {

    fun recordInbound(phone: String, at: LocalDateTime = LocalDateTime.now()): WhatsAppPhoneSession {
        val expiresAt = at.plusHours(SERVICE_WINDOW_HOURS)
        val existing = phoneSessionRepository.findById(phone).orElse(null)
        val session = if (existing != null) {
            existing.copy(
                serviceWindowExpiresAt = expiresAt,
                updatedAt = at
            )
        } else {
            WhatsAppPhoneSession(
                phone = phone,
                serviceWindowExpiresAt = expiresAt,
                updatedAt = at
            )
        }
        return phoneSessionRepository.save(session)
    }

    fun getServiceWindow(phone: String): WhatsAppServiceWindowStatus {
        return getServiceWindows(listOf(phone)).getValue(phone)
    }

    fun getServiceWindows(phones: Collection<String>): Map<String, WhatsAppServiceWindowStatus> {
        val distinct = phones.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (distinct.isEmpty()) return emptyMap()
        val sessions = phoneSessionRepository.findAllById(distinct).associateBy { it.phone }
        val now = LocalDateTime.now()
        return distinct.associateWith { phone ->
            val session = sessions[phone]
            val expiresAt = session?.serviceWindowExpiresAt
            val open = expiresAt != null && expiresAt.isAfter(now)
            WhatsAppServiceWindowStatus(
                phone = phone,
                open = open,
                expiresAt = expiresAt,
                lastInboundAt = session?.updatedAt
            )
        }
    }

    data class WhatsAppServiceWindowStatus(
        val phone: String,
        val open: Boolean,
        val expiresAt: LocalDateTime?,
        val lastInboundAt: LocalDateTime? = null
    )

    companion object {
        const val SERVICE_WINDOW_HOURS = 24L
    }
}
