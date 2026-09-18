package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.events.GigafiberRedisProperties
import com.dscorp.wispadmin.events.PlatformEventCodec
import com.dscorp.wispadmin.events.PlatformEventTypes
import com.dscorp.wispadmin.events.RedisStreamPump
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "gigafiber.redis", name = ["enabled"], havingValue = "true")
class CpeInformEventConsumer(
    redis: StringRedisTemplate,
    private val properties: GigafiberRedisProperties,
    private val persist: CpeInformPersistService,
) {
    private val logger = LoggerFactory.getLogger(CpeInformEventConsumer::class.java)
    private val pump = RedisStreamPump(redis, properties, properties.informConsumerGroup)

    fun poll() {
        if (properties.enabled) pump.poll { applyFields(it) }
    }

    fun applyFields(fields: Map<String, String>) {
        val event = PlatformEventCodec.fromFields(fields)
        if (event.type != PlatformEventTypes.CPE_INFORM) return
        try {
            persist.persistFromEventJson(event.payloadJson)
        } catch (ex: Exception) {
            logger.warn("cpe.inform persist failed sn={}: {}", event.sn, ex.message)
        }
    }
}
