package com.dscorp.wispadmin.wispadmin.util.fcm

data class FcmMessage(
    val id: String = "",
    val title: String,
    val type: FcmMessageType,
    val message: String,
    val data: Any? = null,
    val topic: String? = null,
    val customerToken: String? = null
) {
    enum class FcmMessageType {
        PAYMENT, ADVERTISING, GENERAL, INFO, ASSISTANCE_TICKET, PAYMENT_CRITICAL, PAYMENT_WARNING, PAYMENT_INFO, PAYMENT_SUCCESS, APP_MANAGEMENT,INSTALLATION_ORDER, TECHNICIAN_ASSIGNED_INSTALLATION_ORDER, SALES_ASSIGNED_INSTALLATION_ORDER,SALES_CLOSED_INSTALLATION_ORDER
    }

}