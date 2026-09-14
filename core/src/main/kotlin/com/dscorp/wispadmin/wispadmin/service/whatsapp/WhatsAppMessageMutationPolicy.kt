package com.dscorp.wispadmin.wispadmin.service.whatsapp

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Time-window rules for editing / soft-deleting outbound operator messages.
 * Meta Cloud API does not support business-side edit/delete of sent messages;
 * these rules gate local CRM mutations and UI affordances.
 */
object WhatsAppMessageMutationPolicy {

    val EDIT_WINDOW: Duration = Duration.ofMinutes(15)
    val DELETE_WINDOW: Duration = Duration.ofHours(24)

    const val EDIT_EXPIRED_MESSAGE = "El tiempo límite de 15 minutos para editar ha expirado"
    const val DELETE_EXPIRED_MESSAGE = "El tiempo límite de 24 horas para eliminar ha expirado"
    const val EDIT_NOT_PLAIN_TEXT_MESSAGE = "Solo se pueden editar mensajes de texto salientes."
    const val NO_LOCAL_OUTBOUND_RECORD =
        "No se puede editar/eliminar este mensaje porque no existe un registro local de envío"

    fun canEditOutbound(
        direction: String?,
        messageType: String?,
        createdAt: LocalDateTime?,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (!direction.equals("OUTBOUND", ignoreCase = true)) return false
        if (createdAt == null) return false
        if (!isPlainTextOutbound(messageType)) return false
        return withinWindow(createdAt, EDIT_WINDOW, now, zone)
    }

    fun canDeleteOutbound(
        direction: String?,
        createdAt: LocalDateTime?,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (!direction.equals("OUTBOUND", ignoreCase = true)) return false
        if (createdAt == null) return false
        return withinWindow(createdAt, DELETE_WINDOW, now, zone)
    }

    fun requireEditableOutbound(
        direction: String?,
        messageType: String?,
        createdAt: LocalDateTime?,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        if (!direction.equals("OUTBOUND", ignoreCase = true)) {
            throw IllegalArgumentException(EDIT_NOT_PLAIN_TEXT_MESSAGE)
        }
        if (!isPlainTextOutbound(messageType)) {
            throw IllegalArgumentException(EDIT_NOT_PLAIN_TEXT_MESSAGE)
        }
        if (createdAt == null || !withinWindow(createdAt, EDIT_WINDOW, now, zone)) {
            throw IllegalArgumentException(EDIT_EXPIRED_MESSAGE)
        }
    }

    fun requireDeletableOutbound(
        direction: String?,
        createdAt: LocalDateTime?,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        if (!direction.equals("OUTBOUND", ignoreCase = true)) {
            throw IllegalArgumentException(DELETE_EXPIRED_MESSAGE)
        }
        if (createdAt == null || !withinWindow(createdAt, DELETE_WINDOW, now, zone)) {
            throw IllegalArgumentException(DELETE_EXPIRED_MESSAGE)
        }
    }

    fun isPlainTextOutbound(messageType: String?): Boolean {
        val normalized = messageType?.trim()?.uppercase().orEmpty()
        return normalized in setOf(
            "OPERATOR_REPLY",
            "AUTO_REPLY",
            "TEXT",
            "",
        )
    }

    private fun withinWindow(
        createdAt: LocalDateTime,
        window: Duration,
        now: Instant,
        zone: ZoneId,
    ): Boolean {
        val createdInstant = createdAt.atZone(zone).toInstant()
        return !createdInstant.isAfter(now) && Duration.between(createdInstant, now) <= window
    }
}
