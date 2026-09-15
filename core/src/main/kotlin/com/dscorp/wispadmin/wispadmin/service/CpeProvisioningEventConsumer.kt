package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.events.GigafiberRedisProperties
import com.dscorp.wispadmin.events.PlatformEventCodec
import com.dscorp.wispadmin.events.PlatformEventTypes
import com.dscorp.wispadmin.events.RedisStreamPump
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "gigafiber.redis", name = ["enabled"], havingValue = "true")
class CpeProvisioningEventConsumer(
    redis: StringRedisTemplate,
    private val properties: GigafiberRedisProperties,
    private val flags: CpeProvisionFlagService,
    private val json: ObjectMapper,
) {
    private val pump = RedisStreamPump(redis, properties, properties.cpeConsumerGroup)

    fun poll() {
        if (properties.enabled) pump.poll { applyFields(it) }
    }

    fun applyFields(fields: Map<String, String>) {
        val event = PlatformEventCodec.fromFields(fields)
        if (event.type != PlatformEventTypes.CPE_PROVISIONING) return
        val sn = event.sn ?: return
        val status = json.readTree(event.payloadJson).path("cpeStatus").asText(null) ?: return
        flags.apply(sn, status, event.occurredAt, event.eventId)
    }
}
