package com.dscorp.wispadmin.events

import java.time.Instant

data class PlatformEvent(
    val type: String,
    val subscriptionId: Int? = null,
    val sn: String? = null,
    val occurredAt: Instant,
    val payloadJson: String = "{}",
)

object PlatformEventTypes {
    const val TRAFFIC_LATEST = "traffic.latest"
    const val TRAFFIC_POLL_RUN = "traffic.poll-run"
    const val TRAFFIC_ANOMALY_OPENED = "traffic.anomaly-opened"
    const val TRAFFIC_ANOMALY_CLEARED = "traffic.anomaly-cleared"
    const val ONU_OPTICAL = "onu.optical"
    const val ONU_STATE = "onu.state"
    const val CPE_PROVISIONING = "cpe.provisioning"
}

object PlatformEventCodec {
    fun toFields(event: PlatformEvent): Map<String, String> {
        val fields = linkedMapOf(
            "type" to event.type,
            "occurredAt" to event.occurredAt.toString(),
            "payloadJson" to event.payloadJson,
        )
        event.subscriptionId?.let { fields["subscriptionId"] = it.toString() }
        event.sn?.takeIf { it.isNotBlank() }?.let { fields["sn"] = it }
        return fields
    }

    fun fromFields(fields: Map<String, String>): PlatformEvent {
        return PlatformEvent(
            type = fields["type"].orEmpty(),
            subscriptionId = fields["subscriptionId"]?.takeIf { it.isNotBlank() }?.toIntOrNull(),
            sn = fields["sn"]?.takeIf { it.isNotBlank() },
            occurredAt = fields["occurredAt"]?.let { Instant.parse(it) } ?: Instant.EPOCH,
            payloadJson = fields["payloadJson"] ?: "{}",
        )
    }
}

interface EventBusPort {
    fun publish(event: PlatformEvent)
}

class NoOpEventBus : EventBusPort {
    override fun publish(event: PlatformEvent) = Unit
}

class RecordingEventBus : EventBusPort {
    val published = mutableListOf<PlatformEvent>()
    override fun publish(event: PlatformEvent) {
        published += event
    }
}
