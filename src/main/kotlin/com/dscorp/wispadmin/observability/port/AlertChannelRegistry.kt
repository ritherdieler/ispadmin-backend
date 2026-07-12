package com.dscorp.wispadmin.observability.port

import com.dscorp.wispadmin.observability.entity.ObsAlertChannelType
import org.springframework.stereotype.Component

@Component
class AlertChannelRegistry(
    adapters: List<AlertChannelPort>
) {
    private val byType: Map<ObsAlertChannelType, AlertChannelPort> =
        adapters.associateBy { it.channelType }

    fun forType(type: ObsAlertChannelType): AlertChannelPort? = byType[type]
}
