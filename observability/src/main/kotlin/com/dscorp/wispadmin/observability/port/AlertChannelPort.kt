package com.dscorp.wispadmin.observability.port

import com.dscorp.wispadmin.observability.entity.ObsAlertChannel
import com.dscorp.wispadmin.observability.entity.ObsAlertChannelType

data class AlertNotification(
    val title: String,
    val message: String,
    val severity: String?,
    val type: String,
    val issueUrl: String? = null
)

data class AlertChannelSendResult(
    val ok: Boolean,
    val detail: String?
)

interface AlertChannelPort {
    val channelType: ObsAlertChannelType

    fun send(channel: ObsAlertChannel, notification: AlertNotification): AlertChannelSendResult
}
