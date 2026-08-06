package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppAudioPayload
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppDocumentPayload
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppMediaIdPayload
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppMediaMessageBody
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppMessageContext

/**
 * Builds Meta Cloud API media message bodies.
 * Audio must never include [caption] — Meta returns HTTP 400 (Error 100) for that key.
 */
object WhatsAppMediaMessagePayloadBuilder {

    fun build(
        phoneNumber: String,
        kind: WhatsAppOutboundMediaKind,
        mediaId: String,
        caption: String? = null,
        filename: String? = null,
        contextMessageId: String? = null,
        voiceNote: Boolean? = null,
        normalizePhone: (String) -> String = { PeruvianWhatsAppPhone.toInternational(it) },
    ): WhatsAppMediaMessageBody {
        val type = WhatsAppMediaConstraints.metaType(kind)
        val context = contextMessageId?.takeIf { it.isNotBlank() }?.let { WhatsAppMessageContext(it) }
        val trimmedCaption = caption?.trim()?.takeIf { it.isNotEmpty() }?.take(1024)
        val to = normalizePhone(phoneNumber)

        return when (kind) {
            WhatsAppOutboundMediaKind.IMAGE -> WhatsAppMediaMessageBody(
                to = to,
                type = type,
                context = context,
                image = WhatsAppMediaIdPayload(id = mediaId, caption = trimmedCaption),
            )
            WhatsAppOutboundMediaKind.DOCUMENT -> WhatsAppMediaMessageBody(
                to = to,
                type = type,
                context = context,
                document = WhatsAppDocumentPayload(
                    id = mediaId,
                    caption = trimmedCaption,
                    filename = filename?.takeIf { it.isNotBlank() },
                ),
            )
            WhatsAppOutboundMediaKind.AUDIO -> WhatsAppMediaMessageBody(
                to = to,
                type = type,
                context = context,
                // Meta: audio object only supports id/link (+ optional voice) — never caption.
                audio = WhatsAppAudioPayload(
                    id = mediaId,
                    voice = resolveVoiceFlag(voiceNote, filename),
                ),
            )
        }
    }

    private fun resolveVoiceFlag(voiceNote: Boolean?, filename: String?): Boolean? {
        if (voiceNote == true) return true
        if (voiceNote == false) return false
        val name = filename?.lowercase().orEmpty()
        return if (name.startsWith("voice-note") || name.contains("ptt")) true else null
    }
}
