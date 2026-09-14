package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.events.GigafiberRedisProperties
import com.dscorp.wispadmin.events.PlatformEventTypes
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate

class CpeProvisioningEventConsumerTest {
    private val flags = mockk<CpeProvisionFlagService>(relaxed = true)
    private val consumer = CpeProvisioningEventConsumer(
        redis = mockk<StringRedisTemplate>(relaxed = true),
        properties = GigafiberRedisProperties(),
        flags = flags,
        json = ObjectMapper(),
    )

    @Test
    fun `applies COMPLETE from cpe provisioning fields`() {
        consumer.applyFields(
            mapOf(
                "type" to PlatformEventTypes.CPE_PROVISIONING,
                "sn" to "ZTEGDC47BFFD",
                "occurredAt" to "2026-09-04T22:00:00Z",
                "payloadJson" to """{"cpeStatus":"COMPLETE","uniqueExternalId":"ext-1"}""",
            )
        )
        verify { flags.apply("ZTEGDC47BFFD", "COMPLETE", any(), any()) }
    }

    @Test
    fun `ignores non cpe events`() {
        consumer.applyFields(
            mapOf(
                "type" to PlatformEventTypes.TRAFFIC_LATEST,
                "sn" to "ZTEGDC47BFFD",
                "occurredAt" to "2026-09-04T22:00:00Z",
                "payloadJson" to """{"cpeStatus":"COMPLETE"}""",
            )
        )
        verify(exactly = 0) { flags.apply(any(), any(), any(), any()) }
    }
}
