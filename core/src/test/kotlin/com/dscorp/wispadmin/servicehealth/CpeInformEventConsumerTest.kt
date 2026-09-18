package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.events.GigafiberRedisProperties
import com.dscorp.wispadmin.events.PlatformEventTypes
import com.dscorp.wispadmin.servicehealth.service.CpeInformEventConsumer
import com.dscorp.wispadmin.servicehealth.service.CpeInformPersistService
import com.dscorp.wispadmin.servicehealth.service.HealthSummaryQueryService
import com.dscorp.wispadmin.servicehealth.service.IdentityService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Instant

class CpeInformEventConsumerTest {
    private val identity = mockk<IdentityService>()
    private val persist = mockk<CpeInformPersistService>(relaxed = true)
    private val summaries = mockk<HealthSummaryQueryService>(relaxed = true)
    private val props = GigafiberRedisProperties().apply { informConsumerGroup = "wifi-inform-core" }
    private val consumer = CpeInformEventConsumer(
        redis = mockk<StringRedisTemplate>(relaxed = true),
        properties = props,
        identity = identity,
        persist = persist,
        summaries = summaries,
    )

    init {
        every { identity.resolveOnu("12345B4641531C0B6") } returns 42
    }

    @Test
    fun `persists cpe inform and reevaluates`() {
        val informAt = Instant.parse("2026-09-18T05:00:00Z")
        consumer.applyFields(
            mapOf(
                "type" to PlatformEventTypes.CPE_INFORM,
                "sn" to "12345B4641531C0B6",
                "occurredAt" to informAt.toString(),
                "payloadJson" to """{"sn":"12345B4641531C0B6","deviceId":"dev","informAt":"$informAt","complete":true,"associatedDeviceCount":0,"qualityStatus":"FRESH","stations":[]}""",
            )
        )
        verify { persist.persistFromEventJson(any()) }
        verify { summaries.reevaluate(42, informAt) }
    }

    @Test
    fun `ignores traffic latest`() {
        consumer.applyFields(
            mapOf(
                "type" to PlatformEventTypes.TRAFFIC_LATEST,
                "subscriptionId" to "42",
                "occurredAt" to "2026-09-18T05:00:00Z",
                "payloadJson" to """{"mbpsDown":1.0,"mbpsUp":0.1,"sampleStatus":"OK"}""",
            )
        )
        verify(exactly = 0) { persist.persistFromEventJson(any()) }
        verify(exactly = 0) { summaries.reevaluate(any(), any()) }
    }

    @Test
    fun `ignores optical batch`() {
        consumer.applyFields(
            mapOf(
                "type" to PlatformEventTypes.ONU_OPTICAL_BATCH,
                "occurredAt" to "2026-09-18T05:00:00Z",
                "payloadJson" to """{"oltId":2,"slot":1,"port":6,"polledAt":"2026-09-18T05:00:00Z","onus":[]}""",
            )
        )
        verify(exactly = 0) { persist.persistFromEventJson(any()) }
        verify(exactly = 0) { summaries.reevaluate(any(), any()) }
    }

    @Test
    fun `default inform consumer group is wifi-inform-core`() {
        assertEquals("wifi-inform-core", GigafiberRedisProperties().informConsumerGroup)
    }
}
