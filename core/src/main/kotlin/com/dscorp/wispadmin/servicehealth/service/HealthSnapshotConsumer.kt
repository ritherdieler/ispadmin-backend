package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.events.GigafiberRedisProperties
import com.dscorp.wispadmin.events.PlatformEventCodec
import com.dscorp.wispadmin.events.RedisStreamPump
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "gigafiber.redis", name = ["enabled"], havingValue = "true")
class HealthSnapshotConsumer(
    redis: StringRedisTemplate,
    private val properties: GigafiberRedisProperties,
    private val ingest: HealthSnapshotIngestService,
) {
    private val pump = RedisStreamPump(redis, properties, properties.consumerGroup)

    fun poll() {
        if (properties.enabled) pump.poll { ingest.apply(PlatformEventCodec.fromFields(it)) }
    }
}
