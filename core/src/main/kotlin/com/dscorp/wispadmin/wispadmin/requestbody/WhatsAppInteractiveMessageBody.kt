package com.dscorp.wispadmin.wispadmin.requestbody

import com.fasterxml.jackson.annotation.JsonInclude

data class WhatsAppInteractiveButton(
    val id: String,
    val title: String
)

data class WhatsAppInteractiveReplyBody(
    val messaging_product: String = "whatsapp",
    val recipient_type: String = "individual",
    val to: String,
    val type: String = "interactive",
    val interactive: WhatsAppInteractiveContent,
    val context: WhatsAppMessageContext? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class WhatsAppInteractiveContent(
    val type: String = "button",
    val body: WhatsAppInteractiveText,
    val action: WhatsAppInteractiveAction,
    val footer: WhatsAppInteractiveText? = null
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

data class WhatsAppInteractiveListReplyBody(
    val messaging_product: String = "whatsapp",
    val recipient_type: String = "individual",
    val to: String,
    val type: String = "interactive",
    val interactive: WhatsAppInteractiveListContent,
    val context: WhatsAppMessageContext? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class WhatsAppInteractiveListContent(
    val type: String = "list",
    val body: WhatsAppInteractiveText,
    val action: WhatsAppInteractiveListAction,
    val footer: WhatsAppInteractiveText? = null
)

data class WhatsAppInteractiveListAction(
    val button: String,
    val sections: List<WhatsAppInteractiveListSection>
)

data class WhatsAppInteractiveListSection(
    val title: String,
    val rows: List<WhatsAppInteractiveListRow>
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class WhatsAppInteractiveListRow(
    val id: String,
    val title: String,
    val description: String? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class WhatsAppMarkReadBody(
    val messaging_product: String = "whatsapp",
    val status: String = "read",
    val message_id: String,
    val typing_indicator: WhatsAppTypingIndicator? = null
)

data class WhatsAppTypingIndicator(
    val type: String = "text"
)

data class WhatsAppThreadControlRecipient(
    val id: String
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class WhatsAppPassThreadControlBody(
    val recipient: WhatsAppThreadControlRecipient,
    val target_app_id: String,
    val metadata: String? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class WhatsAppTakeThreadControlBody(
    val recipient: WhatsAppThreadControlRecipient,
    val metadata: String? = null
)
