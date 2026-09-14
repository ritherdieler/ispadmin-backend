package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.events.GigafiberRedisProperties
import com.dscorp.wispadmin.events.PlatformEventCodec
import com.dscorp.wispadmin.events.PlatformEventTypes
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import javax.annotation.PostConstruct

@Service
@ConditionalOnProperty(prefix = "gigafiber.redis", name = ["enabled"], havingValue = "true")
class CpeProvisioningEventConsumer(
    private val redis: StringRedisTemplate,
    private val properties: GigafiberRedisProperties,
    private val flags: CpeProvisionFlagService,
    private val json: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(CpeProvisioningEventConsumer::class.java)
    private val group: String get() = properties.cpeConsumerGroup

    private val pump = com.dscorp.wispadmin.events.RedisStreamPump(redis, properties, properties.cpeConsumerGroup)

    @Scheduled(fixedDelayString = "\${gigafiber.redis.consumer-interval-ms:1000}")
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
