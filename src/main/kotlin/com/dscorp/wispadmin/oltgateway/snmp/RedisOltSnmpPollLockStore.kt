package com.dscorp.wispadmin.oltgateway.snmp

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import java.time.Duration
import java.util.concurrent.TimeUnit

class RedisOltSnmpPollLockStore(
    private val redis: StringRedisTemplate,
) : OltSnmpPollLockStore {

    companion object {
        private val RELEASE = DefaultRedisScript(
            """
            if redis.call("get", KEYS[1]) == ARGV[1] then
              return redis.call("del", KEYS[1])
            else
              return 0
            end
            """.trimIndent(),
            Long::class.java
        )
    }

    override fun tryAcquire(key: String, ownerId: String, ttl: Duration): Boolean {
        return redis.opsForValue().setIfAbsent(key, ownerId, ttl) == true
    }

    override fun forceAcquire(key: String, ownerId: String, ttl: Duration) {
        redis.opsForValue().set(key, ownerId, ttl)
    }

    override fun ttlRemaining(key: String): Long? {
        val remaining = redis.getExpire(key, TimeUnit.MILLISECONDS) ?: -2L
        if (remaining < 0) return null
        return remaining
    }

    override fun release(key: String, ownerId: String) {
        redis.execute(RELEASE, listOf(key), ownerId)
    }
}
