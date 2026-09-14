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

    private val pump = com.dscorp.wispadmin.events.RedisStreamPump(redis, properties, properties.consumerGroup)

    @Scheduled(fixedDelayString = "\${gigafiber.redis.consumer-interval-ms:1000}")
    fun poll() {
        if (properties.enabled) pump.poll { ingest.apply(PlatformEventCodec.fromFields(it)) }
    }

}
