package com.dscorp.wispadmin.events

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class OpticalBatchFanoutTest {

    @Test
    fun `un optical batch escribe prod y stg`() {
        val properties = GigafiberRedisProperties().apply {
            namespace = "gw"
            stream = "gigafiber.events"
            opticalFanoutNamespaces = "prod,stg"
        }
        val event = PlatformEvent(
            type = PlatformEventTypes.ONU_OPTICAL_BATCH,
            occurredAt = Instant.parse("2026-09-21T00:00:00Z"),
            producer = "oltgateway",
        )

        assertEquals(
            listOf("prod:gigafiber.events", "stg:gigafiber.events"),
            redisStreamKeys(event, properties),
        )
    }

    @Test
    fun `cpe provisioning sigue el namespace del llamante`() {
        val properties = GigafiberRedisProperties().apply {
            namespace = "prod"
            stream = "gigafiber.events"
            opticalFanoutNamespaces = "prod,stg"
        }
        val event = PlatformEvent(
            type = PlatformEventTypes.CPE_PROVISIONING,
            sn = "ZTEGDC47BFFD",
            occurredAt = Instant.parse("2026-09-21T00:00:00Z"),
        )
        EventRouteContext.setNamespace("stg")
        try {
            assertEquals(listOf("stg:gigafiber.events"), redisStreamKeys(event, properties))
        } finally {
            EventRouteContext.clear()
        }
    }
}
