package com.dscorp.wispadmin.wispadmin.requestbody

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class WhatsAppTemplateMessageBody(
    val messaging_product: String = "whatsapp",
    val to: String,
    val type: String = "template",
    val template: WhatsAppTemplate,
    val biz_opaque_callback_data: String? = null
)

data class WhatsAppTemplate(
    val name: String,
    val language: WhatsAppTemplateLanguage,
    val components: List<WhatsAppTemplateComponent>
)

data class WhatsAppTemplateLanguage(
    val code: String
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class WhatsAppTemplateComponent(
    val type: String = "body",
    val sub_type: String? = null,
    val index: String? = null,
    val parameters: List<WhatsAppTemplateParameter>
)

data class WhatsAppTemplateParameter(
    val type: String = "text",
    val parameter_name: String? = null,
    val text: String
)
