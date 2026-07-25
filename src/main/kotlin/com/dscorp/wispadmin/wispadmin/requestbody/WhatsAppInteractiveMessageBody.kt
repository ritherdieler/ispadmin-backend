package com.dscorp.wispadmin.wispadmin.requestbody

data class WhatsAppInteractiveButton(
    val id: String,
    val title: String
)

data class WhatsAppInteractiveReplyBody(
    val messaging_product: String = "whatsapp",
    val recipient_type: String = "individual",
    val to: String,
    val type: String = "interactive",
    val interactive: WhatsAppInteractiveContent
)

data class WhatsAppInteractiveContent(
    val type: String = "button",
    val body: WhatsAppInteractiveText,
    val action: WhatsAppInteractiveAction
)

data class WhatsAppInteractiveText(
    val text: String
)

data class WhatsAppInteractiveAction(
    val buttons: List<WhatsAppInteractiveActionButton>
)

data class WhatsAppInteractiveActionButton(
    val type: String = "reply",
    val reply: WhatsAppInteractiveButton
)

data class WhatsAppMarkReadBody(
    val messaging_product: String = "whatsapp",
    val status: String = "read",
    val message_id: String
)
