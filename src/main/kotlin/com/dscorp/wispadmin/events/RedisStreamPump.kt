package com.dscorp.wispadmin.events

import org.slf4j.LoggerFactory
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.stream.*
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration
import java.util.UUID

/** At-least-once delivery; handlers must be idempotent. */
class RedisStreamPump(
    private val redis: StringRedisTemplate,
    private val properties: GigafiberRedisProperties,
    private val group: String,
    private val consumer: String = UUID.randomUUID().toString(),
) {
    private val log=LoggerFactory.getLogger(javaClass)
    fun poll(handle: (Map<String,String>) -> Unit) {
        val stream=properties.namespaced(properties.stream)
        try {
            try {
                redis.execute<String> { connection ->
                    connection.streamCommands().xGroupCreate(stream.toByteArray(),group,ReadOffset.from("0-0"),true)
                }
            } catch(ex: Exception) {
                if (generateSequence<Throwable>(ex) { it.cause }.none { it.message?.contains("BUSYGROUP")==true }) throw ex
            }
            val ops=redis.opsForStream<String,String>()
            val pending=ops.pending(stream,group,Range.unbounded<String>(),50)
            for (message in pending) {
                if(message.elapsedTimeSinceLastDelivery < Duration.ofMillis(properties.pendingIdleMs)) continue
                val records=ops.claim(stream,group,consumer,Duration.ofMillis(properties.pendingIdleMs),message.id)
                records.forEach { deliver(it,message.totalDeliveryCount+1,handle) }
            }
            val records=ops.read(Consumer.from(group,consumer),StreamReadOptions.empty().count(50),StreamOffset.create(stream,ReadOffset.lastConsumed())).orEmpty()
            records.forEach { deliver(it,1,handle) }
        } catch(ex: Exception) {
            log.warn("Event stream unavailable group={} error={}",group,ex.javaClass.simpleName)
        }
    }
    private fun deliver(record: MapRecord<String,String,String>,attempt: Long,handle: (Map<String,String>) -> Unit) {
        val ops=redis.opsForStream<String,String>()
        try {
            handle(record.value)
            ops.acknowledge(record.stream!!,group,record.id)
        } catch(ex: Exception) {
            if(attempt >= properties.maxDeliveries) {
                // Never acknowledge a poisoned record unless quarantine succeeds.
                val quarantine=record.value + mapOf("originalId" to record.id.value,"errorType" to ex.javaClass.simpleName)
                checkNotNull(ops.add(record.stream+":dead:"+group,quarantine))
                ops.acknowledge(record.stream!!,group,record.id)
            }
            log.warn("Event handler failed group={} id={} attempt={}",group,record.id,attempt)
        }
    }
}
