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

    @PostConstruct
    fun ensureGroup() {
        try {
            redis.execute<String> { connection ->
                connection.streamCommands().xGroupCreate(
                    properties.stream.toByteArray(),
                    group,
                    ReadOffset.from("0-0"),
                    true,
                )
            }
        } catch (ex: Exception) {
            logger.info("Redis CPE consumer group ready or already exists: {}", ex.message)
        }
    }

    @Scheduled(fixedDelayString = "\${gigafiber.redis.cpe-consumer-interval-ms:1000}")
    fun poll() {
        if (!properties.enabled) return
        try {
            val records = redis.opsForStream<String, String>().read(
                Consumer.from(group, "core-cpe-1"),
                StreamReadOptions.empty().count(50).block(Duration.ofMillis(200)),
                StreamOffset.create(properties.stream, ReadOffset.lastConsumed()),
            ) ?: return
            for (record in records) {
                handle(record)
            }
        } catch (ex: Exception) {
            logger.warn("Redis CPE stream consume failed: {}", ex.message)
        }
    }

    fun applyFields(fields: Map<String, String>) {
        val event = PlatformEventCodec.fromFields(fields)
        if (event.type != PlatformEventTypes.CPE_PROVISIONING) return
        val sn = event.sn ?: return
        val status = json.readTree(event.payloadJson).path("cpeStatus").asText(null) ?: return
        flags.apply(sn, status)
    }

    private fun handle(record: MapRecord<String, String, String>) {
        try {
            applyFields(record.value)
            redis.opsForStream<String, String>().acknowledge(properties.stream, group, record.id)
        } catch (ex: Exception) {
            logger.warn("Redis CPE stream record failed id={}: {}", record.id, ex.message)
        }
    }
}
