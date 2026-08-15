package com.dscorp.wispadmin.wispadmin.service

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

@Component
class SubscriptionIntegrityViolationClassifier {

    enum class ViolationType {
        IP,
        OTHER
    }

    fun classify(ex: DataIntegrityViolationException): ViolationType {
        return if (isIpUniqueViolation(ex)) ViolationType.IP else ViolationType.OTHER
    }

    fun isIpUniqueViolation(ex: DataIntegrityViolationException): Boolean {
        val text = buildString {
            append(ex.message.orEmpty())
            append(' ')
            append(ex.rootCause?.message.orEmpty())
            append(' ')
            append(ex.mostSpecificCause?.message.orEmpty())
        }.lowercase()

        if (text.contains("client_request_id") || text.contains("subscription.dni") ||
            text.contains("key 'dni'") || text.contains("key \"dni\"")
        ) {
            return false
        }

        return text.contains("subscription.ip") ||
            text.contains("uk_subscription_ip") ||
            text.contains("key 'ip'") ||
            text.contains("key \"ip\"") ||
            (text.contains("duplicate") && Regex("""['"`]ip['"`]""").containsMatchIn(text))
    }
}
