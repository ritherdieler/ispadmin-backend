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
    private val identity: IdentityService,
    private val persist: CpeInformPersistService,
    private val summaries: HealthSummaryQueryService,
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
            return
        }
        val subscriptionId = event.subscriptionId ?: event.sn?.let { identity.resolveOnu(it) } ?: return
        val startedAt = System.nanoTime()
        try {
            summaries.reevaluate(subscriptionId, event.occurredAt)
        } catch (ex: Exception) {
            logger.warn("Snapshot reevaluate failed subscription={}: {}", subscriptionId, ex.message)
            return
        }
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        if (elapsedMs >= SLOW_REEVALUATE_MS) {
            logger.warn("Slow reevaluate subscription={} type={} elapsedMs={}", subscriptionId, event.type, elapsedMs)
        }
    }

    private companion object {
        const val SLOW_REEVALUATE_MS = 250L
    }
}
