package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppReactionContent
import com.dscorp.wispadmin.wispadmin.requestbody.WhatsAppReactionMessageBody

/**
 * Builds Meta Cloud API reaction payloads.
 * Empty [emoji] removes an existing reaction (Meta contract).
 */
object WhatsAppReactionPayloadBuilder {

    fun build(
        phoneNumber: String,
        wamid: String,
        emoji: String,
        normalizePhone: (String) -> String = { PeruvianWhatsAppPhone.toInternational(it) },
    ): WhatsAppReactionMessageBody {
        require(wamid.isNotBlank()) { "message_id (wamid) es obligatorio para reaccionar." }
        return WhatsAppReactionMessageBody(
            to = normalizePhone(phoneNumber),
            reaction = WhatsAppReactionContent(
                message_id = wamid.trim(),
                emoji = emoji,
            ),
        )
    }
}
