package com.dscorp.wispadmin.events

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.connection.stream.*
import java.time.Instant
import java.util.UUID

@EnabledIfEnvironmentVariable(named="REDIS_TEST_PORT",matches="[0-9]+")
class RedisRecoveryIntegrationTest {
    private fun redis(): Pair<LettuceConnectionFactory,StringRedisTemplate> {
        val factory=LettuceConnectionFactory("127.0.0.1",System.getenv("REDIS_TEST_PORT").toInt())
        factory.afterPropertiesSet()
        return factory to StringRedisTemplate(factory)
    }
    @Test fun `failed delivery is reclaimed after consumer restart and eventually acknowledged`() {
        val (factory,redis)=redis()
        try {
            val props=GigafiberRedisProperties().apply { stream="test:${UUID.randomUUID()}"; pendingIdleMs=0 }
            val event=PlatformEvent("cpe.provisioning",sn="SN",occurredAt=Instant.now())
            RedisStreamEventBus(redis,props).publish(event)
            val first=RedisStreamPump(redis,props,"group","first")
            first.poll { error("temporary failure") }
            assertEquals(1L,redis.opsForStream<String,String>().pending(props.stream,"group")!!.totalPendingMessages)
            val seen=mutableListOf<String>()
            RedisStreamPump(redis,props,"group","second").poll { seen.add(it["eventId"]!!) }
            assertEquals(listOf(event.eventId),seen)
            assertEquals(0L,redis.opsForStream<String,String>().pending(props.stream,"group")!!.totalPendingMessages)
        } finally { factory.destroy() }
    }
    @Test fun `poison event is quarantined before acknowledgement`() {
        val (factory,redis)=redis()
        try {
            val props=GigafiberRedisProperties().apply { stream="test:${UUID.randomUUID()}"; pendingIdleMs=0; maxDeliveries=2 }
            redis.opsForStream<String,String>().add(props.stream,mapOf("bad" to "record"))
            val consumer=RedisStreamPump(redis,props,"group","consumer")
            repeat(4) { consumer.poll { throw IllegalArgumentException("invalid schema") } }
            assertEquals(1L,redis.opsForStream<String,String>().size(props.stream+":dead:group"))
            assertEquals(0L,redis.opsForStream<String,String>().pending(props.stream,"group")!!.totalPendingMessages)
        } finally { factory.destroy() }
    }
    @Test fun `snapshot clears absent values and rejects an older observation`() {
        val (factory,redis)=redis()
        try {
            val props=GigafiberRedisProperties().apply { namespace="test:${UUID.randomUUID()}" }
            val cache=RedisLiveTelemetry(redis,props)
            val now=Instant.now()
            cache.putTraffic(7,LiveTrafficSample(50.0,10.0,now,"OK",2))
            cache.putTraffic(7,LiveTrafficSample(null,null,now.plusSeconds(1),"MISSING",null))
            cache.putTraffic(7,LiveTrafficSample(99.0,99.0,now,"OK",2))
            val result=cache.traffic(7)!!
            assertNull(result.avgMbpsDown)
            assertNull(result.hostDeviceId)
            assertEquals("MISSING",result.sampleStatus)
            assertNull(RedisLiveTelemetry(redis,GigafiberRedisProperties().apply { namespace=props.namespace+":other" }).traffic(7))
        } finally { factory.destroy() }
    }
    @Test fun `optical patch does not refresh state observation time`() {
        val (factory,redis)=redis()
        try {
            val props=GigafiberRedisProperties().apply { namespace="test:${UUID.randomUUID()}" }
            val cache=RedisLiveTelemetry(redis,props)
            val now=Instant.now()
            cache.putOnu(8,LiveOnuState("SN1","online",null,now,updateKind="state"))
            cache.putOnu(8,LiveOnuState("SN1",null,-20.0,now.plusSeconds(1),updateKind="optical"))
            val state=cache.onu(8)!!
            assertEquals("online",state.runState)
            assertEquals(now,state.observedAt)
            assertEquals(-20.0,state.rxPowerDbm)
        } finally { factory.destroy() }
    }

}
