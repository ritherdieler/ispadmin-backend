package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class WelcomeTemplateContext(
    val serviceTitle: String,
    val serviceDetails: String,
    val planName: String,
    val planPrice: String,
    val paymentDay: String,
    val paymentInfo: String
)

object WelcomeVariableMapper {

    private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun buildContext(subscription: Subscription): WelcomeTemplateContext {
        val plan = subscription.plan
        val installationType = subscription.installationType

        return WelcomeTemplateContext(
            serviceTitle = WelcomeServiceCopy.serviceTitle(installationType),
            serviceDetails = WelcomeServiceCopy.serviceDetails(
                installationType = installationType,
                downloadSpeed = plan?.downloadSpeed,
                uploadSpeed = plan?.uploadSpeed
            ),
            planName = plan?.name?.trim()?.takeIf { it.isNotEmpty() } ?: "Plan",
            planPrice = formatPrice(subscription.price ?: plan?.price),
            paymentDay = resolvePaymentDay(subscription.subscriptionDatetime),
            paymentInfo = WelcomeServiceCopy.PAYMENT_INFO
        )
    }

    private fun formatPrice(price: Double?): String {
        if (price == null) {
            return "0.00"
        }
        return String.format("%.2f", price)
    }

    private fun resolvePaymentDay(subscriptionDatetime: LocalDateTime?): String {
        return subscriptionDatetime?.dayOfMonth?.toString() ?: "1"
    }

    fun parseSubscriptionDatetime(raw: Any?): LocalDateTime? {
        return when (raw) {
            null -> null
            is LocalDateTime -> raw
            is java.sql.Timestamp -> raw.toLocalDateTime()
            is java.util.Date -> LocalDateTime.ofInstant(raw.toInstant(), java.time.ZoneId.systemDefault())
            else -> {
                val text = raw.toString().trim()
                if (text.isEmpty()) {
                    null
                } else {
                    runCatching { LocalDateTime.parse(text.replace(" ", "T")) }
                        .recoverCatching { LocalDateTime.parse(text, DATE_TIME_FORMATTER) }
                        .getOrNull()
                }
            }
        }
    }
}
