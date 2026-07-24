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
        set(value) { field = value.trim() }
    var paymentReminderTemplateName: String = ""
    var paymentReminderTemplateLanguage: String = "es_PE"
    var paymentReminderMode: String = "text"
    var webhookVerifyToken: String = ""
    var appSecret: String = ""
    var backoffice: WhatsAppBackofficeProperties = WhatsAppBackofficeProperties()
    var welcomeOnRegistration: WhatsAppWelcomeOnRegistrationProperties = WhatsAppWelcomeOnRegistrationProperties()

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

class WhatsAppBackofficeProperties {
    var validationPaidDays: Int = 7
    var welcomeInstalledDays: Int = 30
}

class WhatsAppWelcomeOnRegistrationProperties {
    var enabled: Boolean = false
}
