package com.dscorp.wispadmin.traffic.adapter

import com.dscorp.wispadmin.traffic.config.TrafficProperties
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficSample
import com.dscorp.wispadmin.traffic.entity.TrafficAnomalyEvent
import com.dscorp.wispadmin.traffic.entity.TrafficAnomalyType
import com.dscorp.wispadmin.traffic.entity.TrafficSampleStatus
import com.dscorp.wispadmin.traffic.entity.TrafficSourceRun
import com.dscorp.wispadmin.traffic.entity.TrafficSourceRunStatus
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficSampleRepository
import com.dscorp.wispadmin.traffic.repository.TrafficAnomalyEventRepository
import com.dscorp.wispadmin.traffic.repository.TrafficSourceRunRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import java.time.LocalDateTime

class HealthTrafficAdapterTest {

    private val samples = mockk<SubscriptionTrafficSampleRepository>()
    private val runs = mockk<TrafficSourceRunRepository>()
    private val anomalies = mockk<TrafficAnomalyEventRepository>()
    private val adapter = HealthTrafficAdapter(samples, runs, anomalies, TrafficProperties())

    @Test
    fun `latest sample maps status and queue`() {
        every { samples.findTopBySubscriptionIdOrderByBucketStartDesc(9) } returns SubscriptionTrafficSample(
            id = 3L,
            hostDeviceId = 44,
            sampleStatus = TrafficSampleStatus.OK,
            avgMbpsDown = 12.0,
            queueId = "q-1"
        )

        val sample = adapter.latestSample(9)

        assertEquals(3L, sample?.id)
        assertEquals("OK", sample?.sampleStatus)
        assertEquals("q-1", sample?.queueId)
        assertEquals(12.0, sample?.avgMbpsDown)
    }

    @Test
    fun `missing sample and run degrade to null`() {
        every { samples.findTopBySubscriptionIdOrderByBucketStartDesc(1) } returns null
        every { runs.findTopByHostDeviceIdOrderByStartedAtDesc(2) } returns null
        assertNull(adapter.latestSample(1))
        assertNull(adapter.latestRun(2))
    }

    @Test
    fun `anomaly changes flatten enums`() {
        val at = LocalDateTime.of(2026, 8, 31, 12, 0)
        every { anomalies.findChanges(at, 0L, any()) } returns listOf(
            TrafficAnomalyEvent(
                id = 8L,
                subscriptionId = 1,
                hostDeviceId = 4,
                anomalyType = TrafficAnomalyType.PLAN_SATURATION,
                lastEvaluatedAt = at,
                coveragePct = 91.0,
                confidence = 0.7,
                evidenceJson = "{}"
            )
        )

        val result = adapter.findAnomalyChanges(at, 0L, PageRequest.of(0, 10))

        assertEquals(1, result.size)
        assertEquals("PLAN_SATURATION", result.single().anomalyType)
        assertEquals("OPEN", result.single().eventStatus)
        assertEquals(80.0, adapter.minimumCoveragePct())
    }

    @Test
    fun `latest run exposes status name`() {
        every { runs.findTopByHostDeviceIdOrderByStartedAtDesc(4) } returns TrafficSourceRun(
            id = 5L,
            status = TrafficSourceRunStatus.FAILED
        )
        assertEquals("FAILED", adapter.latestRun(4)?.status)
    }
}
