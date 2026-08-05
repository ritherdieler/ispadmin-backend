package com.dscorp.wispadmin.wispadmin.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "whatsapp")
class WhatsAppProperties {

    var apiVersion: String = ""
    var phoneNumberId: String = ""
    var businessAccountId: String = ""
    var accessToken: String = ""
        set(value) { field = value.trim() }
    var paymentReminderTemplateName: String = ""
    var paymentReminderTemplateLanguage: String = "es_PE"
    var paymentReminderMode: String = "text"
    var webhookVerifyToken: String = ""
    var appSecret: String = ""
    /** When false (local only), inbound Meta webhooks are accepted without HMAC check. */
    var webhookSignatureRequired: Boolean = true
    var backoffice: WhatsAppBackofficeProperties = WhatsAppBackofficeProperties()
    var welcomeOnRegistration: WhatsAppWelcomeOnRegistrationProperties = WhatsAppWelcomeOnRegistrationProperties()
    var autoReply: WhatsAppAutoReplyProperties = WhatsAppAutoReplyProperties()
    var inboundAlert: WhatsAppInboundAlertProperties = WhatsAppInboundAlertProperties()
    var handover: WhatsAppHandoverProperties = WhatsAppHandoverProperties()
    var mediaStorageDir: String = "./data/whatsapp/media"

    fun businessAccountUrl(): String {
        return "${graphApiBaseUrl()}/$businessAccountId"
    }

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

    fun isWebhookConfigured(): Boolean {
        return webhookVerifyToken.isNotBlank() && appSecret.isNotBlank()
    }
}

class WhatsAppBackofficeProperties {
    var validationPaidDays: Int = 7
    var welcomeInstalledDays: Int = 30
}

class WhatsAppWelcomeOnRegistrationProperties {
    var enabled: Boolean = false
}

class WhatsAppAutoReplyProperties {
    var secretaryPhones: String = "948332929,960077993"
    var secretaryHours: String = "Lunes a sabado de 8:00 a.m. a 6:00 p.m."
    var bcpAccount: String = "335-98410-54-0-22"
    var yapePlin: String = "958073976"
    var paymentHolder: String = "GIGAFIBERPERU"
    var ticketDedupHours: Int = 24
    var businessHours: String = "MON-SAT|08:00-18:00"
    var afterHoursMessage: String =
        "Nuestro equipo atendera en horario laboral (Lun-Sab 8:00 a 18:00). Deje su mensaje y le responderemos."
    var operatorSilenceMinutes: Int = 45
    var inboundBurstSeconds: Int = 30
    var menuCooldownMinutes: Int = 20
    var sessionTimeoutMinutes: Int = 10
    var advisorWaitTimeoutMinutes: Int = 120

    fun secretaryPhoneList(): List<String> {
        return secretaryPhones.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
}

class WhatsAppInboundAlertProperties {
    var enabled: Boolean = true
    var cooldownMinutes: Int = 3
}

class WhatsAppHandoverProperties {
    var enabled: Boolean = false
    var targetAppId: String = ""
}
