package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.events.GigafiberRedisProperties
import com.dscorp.wispadmin.events.PlatformEventCodec
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
class HealthSnapshotConsumer(
    private val redis: StringRedisTemplate,
    private val properties: GigafiberRedisProperties,
    private val ingest: HealthSnapshotIngestService,
) {
    private val logger = LoggerFactory.getLogger(HealthSnapshotConsumer::class.java)

    @PostConstruct
    fun ensureGroup() {
        try {
            redis.execute<String> { connection ->
                connection.streamCommands().xGroupCreate(
                    properties.stream.toByteArray(),
                    properties.consumerGroup,
                    ReadOffset.from("0-0"),
                    true,
                )
            }
        } catch (ex: Exception) {
            logger.info("Redis consumer group ready or already exists: {}", ex.message)
        }
    }

    @Scheduled(fixedDelayString = "\${gigafiber.redis.consumer-interval-ms:1000}")
    fun poll() {
        if (!properties.enabled) return
        try {
            val records = redis.opsForStream<String, String>().read(
                Consumer.from(properties.consumerGroup, "core-1"),
                StreamReadOptions.empty().count(50).block(Duration.ofMillis(200)),
                StreamOffset.create(properties.stream, ReadOffset.lastConsumed()),
            ) ?: return
            for (record in records) {
                handle(record)
            }
        } catch (ex: Exception) {
            logger.warn("Redis stream consume failed: {}", ex.message)
        }
    }

    private fun handle(record: MapRecord<String, String, String>) {
        try {
            ingest.apply(PlatformEventCodec.fromFields(record.value))
            redis.opsForStream<String, String>().acknowledge(properties.stream, properties.consumerGroup, record.id)
        } catch (ex: Exception) {
            logger.warn("Redis stream record failed id={}: {}", record.id, ex.message)
        }
    }
}
