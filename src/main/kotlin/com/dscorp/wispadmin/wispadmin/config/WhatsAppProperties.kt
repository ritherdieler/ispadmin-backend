package com.dscorp.wispadmin.wispadmin.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "whatsapp")
class WhatsAppProperties {

    var apiVersion: String =""
    var phoneNumberId: String = ""
    var businessAccountId: String = ""
    var accessToken: String = ""
    var paymentReminderTemplateName: String = ""
    var paymentReminderTemplateLanguage: String = "es_PE"
    var paymentReminderMode: String = "text"

    fun graphApiBaseUrl(): String {
        return "https://graph.facebook.com/$apiVersion"
    }

    fun messagesUrl(): String {
        return "${graphApiBaseUrl()}/$phoneNumberId/messages"
    }

    fun isConfigured(): Boolean {
        return apiVersion.isNotBlank() &&
                phoneNumberId.isNotBlank() &&
                businessAccountId.isNotBlank() &&
                accessToken.isNotBlank()
    }
}
