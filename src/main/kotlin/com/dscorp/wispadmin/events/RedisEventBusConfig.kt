package com.dscorp.wispadmin.events

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions
import org.springframework.data.redis.connection.stream.StreamRecords
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

@ConfigurationProperties(prefix = "gigafiber.redis")
class GigafiberRedisProperties {
    var namespace: String = ""
    var pendingIdleMs: Long = 60_000
    var maxDeliveries: Long = 5
    fun namespaced(key: String): String = if (namespace.isBlank()) key else "$namespace:$key"
    var enabled: Boolean = false
    var host: String = "127.0.0.1"
    var port: Int = 6379
    var password: String = ""
    var stream: String = "gigafiber.events"
    var streamMaxlen: Long = 20_000
    var consumerGroup: String = "snapshot-core"
    var cpeConsumerGroup: String = "cpe-provision-core"
    var cacheTtlSeconds: Long = 15
}

@Configuration
@EnableConfigurationProperties(GigafiberRedisProperties::class)
class RedisEventBusConfig {
    @Bean
    @ConditionalOnProperty(prefix = "gigafiber.redis", name = ["enabled"], havingValue = "true")
    fun gigafiberRedisConnectionFactory(properties: GigafiberRedisProperties): LettuceConnectionFactory {
        val standalone = RedisStandaloneConfiguration(properties.host, properties.port)
        if (properties.password.isNotBlank()) {
            standalone.setPassword(properties.password)
        }
        val factory = LettuceConnectionFactory(standalone)
        factory.afterPropertiesSet()
        return factory
    }

    @Bean
    @ConditionalOnProperty(prefix = "gigafiber.redis", name = ["enabled"], havingValue = "true")
    fun gigafiberStringRedisTemplate(factory: LettuceConnectionFactory): StringRedisTemplate {
        return StringRedisTemplate(factory)
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "gigafiber.redis", name = ["enabled"], havingValue = "true")
    fun redisStreamEventBus(
        redis: StringRedisTemplate,
        properties: GigafiberRedisProperties,
    ): EventBusPort = RedisStreamEventBus(redis, properties)

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "gigafiber.redis", name = ["enabled"], havingValue = "true")
    fun redisHealthSnapshotCache(
        redis: StringRedisTemplate,
        properties: GigafiberRedisProperties,
    ): HealthSnapshotCache = RedisHealthSnapshotCache(redis, properties)

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "gigafiber.redis", name = ["enabled"], havingValue = "true")
    fun redisLiveTelemetry(redis: StringRedisTemplate, properties: GigafiberRedisProperties): LiveTelemetryPort = RedisLiveTelemetry(redis, properties)

    @Bean
    @ConditionalOnMissingBean(EventBusPort::class)
    fun noOpEventBus(): EventBusPort = NoOpEventBus()

    @Bean
    @ConditionalOnMissingBean(HealthSnapshotCache::class)
    fun noOpHealthSnapshotCache(): HealthSnapshotCache = NoOpHealthSnapshotCache()

    @Bean
    @ConditionalOnMissingBean(LiveTelemetryPort::class)
    fun noOpLiveTelemetry(): LiveTelemetryPort = NoOpLiveTelemetry()
}

class RedisStreamEventBus(
    private val redis: StringRedisTemplate,
    private val properties: GigafiberRedisProperties,
) : EventBusPort {
    private val logger = LoggerFactory.getLogger(RedisStreamEventBus::class.java)

    override fun publish(event: PlatformEvent) { tryPublish(event) }

    override fun tryPublish(event: PlatformEvent): Boolean {
        return try {
            val fields = PlatformEventCodec.toFields(event)
            val options = XAddOptions.maxlen(properties.streamMaxlen).approximateTrimming(true)
            val body = fields.entries.associate { it.key.toByteArray() to it.value.toByteArray() }
            redis.execute<Any> { connection ->
                connection.streamCommands().xAdd(
                    StreamRecords.mapBacked<ByteArray, ByteArray, ByteArray>(body)
                        .withStreamKey(properties.namespaced(properties.stream).toByteArray()),
                    options,
                )
            }
            true
        } catch (ex: Exception) {
            logger.warn("Redis stream publish failed type={}: {}", event.type, ex.message)
            false
        }
    }
}

class RedisHealthSnapshotCache(
    private val redis: StringRedisTemplate,
    private val properties: GigafiberRedisProperties,
) : HealthSnapshotCache {
    private val logger = LoggerFactory.getLogger(RedisHealthSnapshotCache::class.java)

    override fun getSummaryJson(subscriptionId: Int): String? {
        return try {
            redis.opsForValue().get(key(subscriptionId))
        } catch (ex: Exception) {
            logger.warn("Redis snapshot get failed id={}: {}", subscriptionId, ex.message)
            null
        }
    }

    override fun putSummaryJson(subscriptionId: Int, json: String, ttl: Duration) {
        try {
            val effective = if (ttl.isZero || ttl.isNegative) Duration.ofSeconds(properties.cacheTtlSeconds) else ttl
            redis.opsForValue().set(key(subscriptionId), json, effective)
        } catch (ex: Exception) {
            logger.warn("Redis snapshot put failed id={}: {}", subscriptionId, ex.message)
        }
    }

    private fun key(subscriptionId: Int) = properties.namespaced("health:360:$subscriptionId")
}

class RedisLiveTelemetry(
    private val redis: StringRedisTemplate,
    private val properties: GigafiberRedisProperties = GigafiberRedisProperties(),
) : LiveTelemetryPort {
    private val logger = LoggerFactory.getLogger(RedisLiveTelemetry::class.java)

    override fun traffic(subscriptionId: Int): LiveTrafficSample? {
        return try {
            val hash = redis.opsForHash<String, String>().entries(trafficKey(subscriptionId))
            if (hash.isEmpty()) return null
            LiveTrafficSample(
                avgMbpsDown = hash["mbpsDown"]?.toDoubleOrNull(),
                avgMbpsUp = hash["mbpsUp"]?.toDoubleOrNull(),
                collectedAt = hash["collectedAt"]?.let { Instant.parse(it) },
                sampleStatus = hash["sampleStatus"] ?: "OK",
                hostDeviceId = hash["hostDeviceId"]?.toIntOrNull(),
            )
        } catch (ex: Exception) {
            logger.warn("Redis live traffic get failed id={}: {}", subscriptionId, ex.message)
            null
        }
    }

    override fun putTraffic(subscriptionId: Int, sample: LiveTrafficSample) {
        try {
            val hash = mutableMapOf(
                "sampleStatus" to sample.sampleStatus,
            )
            sample.avgMbpsDown?.let { hash["mbpsDown"] = it.toString() }
            sample.avgMbpsUp?.let { hash["mbpsUp"] = it.toString() }
            sample.collectedAt?.let { hash["collectedAt"] = it.toString() }
            sample.hostDeviceId?.let { hash["hostDeviceId"] = it.toString() }
            listOf("mbpsDown","mbpsUp","hostDeviceId").forEach { hash.putIfAbsent(it, "") }
            replaceSnapshot(trafficKey(subscriptionId),hash,requireNotNull(sample.collectedAt),300)
        } catch (ex: Exception) {
            logger.warn("Redis live traffic put failed id={}: {}", subscriptionId, ex.message)
        }
    }

    override fun onu(subscriptionId: Int): LiveOnuState? {
        return try {
            val hash = redis.opsForHash<String, String>().entries(onuKey(subscriptionId))
            if (hash.isEmpty()) return null
            val sn = hash["sn"] ?: return null
            LiveOnuState(
                sn = sn,
                runState = hash["runState"]?.takeIf { it.isNotBlank() },
                rxPowerDbm = hash["rxPowerDbm"]?.toDoubleOrNull(),
                observedAt = hash["observedAt"]?.let { Instant.parse(it) } ?: Instant.EPOCH,
            )
        } catch (ex: Exception) {
            logger.warn("Redis live onu get failed id={}: {}", subscriptionId, ex.message)
            null
        }
    }

    override fun putOnu(subscriptionId: Int, state: LiveOnuState) {
        try {
            val hash = mutableMapOf(
                "sn" to state.sn,
                "observedAt" to state.observedAt.toString(),
            )
            state.runState?.let { hash["runState"] = it }
            state.rxPowerDbm?.let { hash["rxPowerDbm"] = it.toString() }
            if (state.updateKind == "optical") {
                hash.remove("runState")
                hash.remove("observedAt")
                hash["opticalObservedAt"] = state.observedAt.toString()
                hash.putIfAbsent("rxPowerDbm", "")
            } else if (state.updateKind == "state") {
                hash.remove("rxPowerDbm")
                hash.putIfAbsent("runState", "")
            } else listOf("runState","rxPowerDbm").forEach { hash.putIfAbsent(it, "") }
            replaceSnapshot(onuKey(subscriptionId),hash,state.observedAt,900,"_timestamp:${state.updateKind}")
        } catch (ex: Exception) {
            logger.warn("Redis live onu put failed id={}: {}", subscriptionId, ex.message)
        }
    }

    private fun replaceSnapshot(key: String, hash: Map<String,String>, at: Instant, ttl: Long, timestampKey: String = "_timestamp") {
        val script=org.springframework.data.redis.core.script.DefaultRedisScript<Long>("""
            local previous = tonumber(redis.call('HGET', KEYS[1], ARGV[3]) or '-1')
            if previous >= tonumber(ARGV[1]) then return 0 end
            for i=4,#ARGV,2 do redis.call('HSET',KEYS[1],ARGV[i],ARGV[i+1]) end
            redis.call('HSET',KEYS[1],ARGV[3],ARGV[1])
            redis.call('EXPIRE',KEYS[1],ARGV[2])
            return 1
        """.trimIndent(),Long::class.java)
        val args=listOf(at.toEpochMilli().toString(),ttl.toString(),timestampKey)+hash.flatMap { listOf(it.key,it.value) }
        redis.execute(script,listOf(key),*args.toTypedArray())
    }

    private fun trafficKey(id: Int) = properties.namespaced("health:live:$id:traffic")
    private fun onuKey(id: Int) = properties.namespaced("health:live:$id:onu")
}
