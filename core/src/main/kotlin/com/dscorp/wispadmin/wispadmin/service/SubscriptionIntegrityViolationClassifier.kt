package com.dscorp.wispadmin.wispadmin.service

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

@Component
class SubscriptionIntegrityViolationClassifier {

    enum class ViolationType {
        IP,
        OTHER
    }

    enum class UniqueField {
        IP,
        DNI,
        CLIENT_REQUEST_ID,
        PPPOE_USERNAME,
        INSTALLATION_ORDER,
        UNKNOWN
    }

    fun classify(ex: DataIntegrityViolationException): ViolationType {
        return if (isIpUniqueViolation(ex)) ViolationType.IP else ViolationType.OTHER
    }

    fun classifyUniqueField(ex: DataIntegrityViolationException): UniqueField {
        val text = exceptionText(ex)

        return when {
            text.contains("client_request_id") ||
                text.contains("uk_subscription_client_request_id") ||
                text.contains(GENERATED_CLIENT_REQUEST_ID_KEY) -> UniqueField.CLIENT_REQUEST_ID

            text.contains("pppoe_username") ||
                text.contains("uk_subscription_pppoe_username") ||
                text.contains(GENERATED_PPPOE_USERNAME_KEY) -> UniqueField.PPPOE_USERNAME

            text.contains("installation_order_id") ||
                text.contains(GENERATED_INSTALLATION_ORDER_KEY) -> UniqueField.INSTALLATION_ORDER

            text.contains("subscription.dni") ||
                text.contains("key 'dni'") ||
                text.contains("key \"dni\"") ||
                text.contains(GENERATED_DNI_KEY) -> UniqueField.DNI

            text.contains("subscription.ip") ||
                text.contains("uk_subscription_ip") ||
                text.contains("key 'ip'") ||
                text.contains("key \"ip\"") ||
                text.contains(GENERATED_IP_KEY) -> UniqueField.IP

            else -> UniqueField.UNKNOWN
        }
    }

    fun isIpUniqueViolation(ex: DataIntegrityViolationException): Boolean {
        val text = exceptionText(ex)

        if (text.contains("client_request_id") || text.contains("subscription.dni") ||
            text.contains("key 'dni'") || text.contains("key \"dni\"")
        ) {
            return false
        }

        return text.contains("subscription.ip") ||
            text.contains("uk_subscription_ip") ||
            text.contains("key 'ip'") ||
            text.contains("key \"ip\"") ||
            text.contains(GENERATED_IP_KEY) ||
            (text.contains("duplicate") && Regex("""['"`]ip['"`]""").containsMatchIn(text))
    }

    private fun exceptionText(ex: DataIntegrityViolationException): String = buildString {
        append(ex.message.orEmpty())
        append(' ')
        append(ex.rootCause?.message.orEmpty())
        append(' ')
        append(ex.mostSpecificCause?.message.orEmpty())
    }.lowercase()

    companion object {
        // Hibernate 5.6 generates these names for unnamed unique constraints.
        private const val GENERATED_DNI_KEY = "uk_ha9a6qa9qabsx9wpgaqojs66f"
        private const val GENERATED_IP_KEY = "uk_7l1j33cejhig146746bvi28fk"
        private const val GENERATED_PPPOE_USERNAME_KEY = "uk_bkdloirok3rf2nt5awvqua6d4"
        private const val GENERATED_CLIENT_REQUEST_ID_KEY = "uk_2a8chskmj8lhn0s4k8nng1a5x"
        private const val GENERATED_INSTALLATION_ORDER_KEY = "uk_9xy84hnugp6i9cv3lry0cky7c"
    }
}
