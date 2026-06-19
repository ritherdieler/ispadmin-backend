package com.dscorp.wispadmin.wispadmin.requestbody

data class WhatsAppTemplateMessageBody(
    val messaging_product: String = "whatsapp",
    val to: String,
    val type: String = "template",
    val template: WhatsAppTemplate
)

data class WhatsAppTemplate(
    val name: String,
    val language: WhatsAppTemplateLanguage,
    val components: List<WhatsAppTemplateComponent>
)

data class WhatsAppTemplateLanguage(
    val code: String
)

data class WhatsAppTemplateComponent(
    val type: String = "body",
    val parameters: List<WhatsAppTemplateParameter>
)

data class WhatsAppTemplateParameter(
    val type: String = "text",
    val parameter_name: String? = null,
    val text: String
)
