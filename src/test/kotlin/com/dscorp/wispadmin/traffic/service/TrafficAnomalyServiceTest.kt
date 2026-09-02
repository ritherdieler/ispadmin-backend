package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.traffic.config.TrafficProperties
import com.dscorp.wispadmin.traffic.entity.*
import com.dscorp.wispadmin.traffic.repository.*
import io.mockk.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class TrafficAnomalyServiceTest {
    @Test
    fun `abre evidencia no causal cuando cobertura del router es insuficiente`() {
        val hourly = mockk<SubscriptionTrafficHourlyRepository>(); val daily = mockk<SubscriptionTrafficDailyRepository>(); val sources = mockk<TrafficSourceRunRepository>(); val anomalies = mockk<TrafficAnomalyEventRepository>()
        every { sources.findTop100ByOrderByStartedAtDesc() } returns listOf(TrafficSourceRun(id = 1, hostDeviceId = 9, expectedCount = 100, writtenCount = 50, status = TrafficSourceRunStatus.PARTIAL))
        every { hourly.findDistinctSubscriptionIdsInBucketRange(any(), any()) } returns emptyList(); every { daily.findDistinctSubscriptionIdsInBucketRange(any(), any()) } returns emptyList(); every { anomalies.findTopByAnomalyTypeAndHostDeviceIdAndEventStatusOrderByStartedAtDesc(any(), any(), any()) } returns null
        val slot = slot<TrafficAnomalyEvent>(); every { anomalies.save(capture(slot)) } answers { firstArg() }
        val service = TrafficAnomalyService(hourly, daily, sources, anomalies, TrafficProperties())
        service.evaluate(LocalDateTime.of(2026, 8, 29, 10, 0))
        assertEquals(TrafficAnomalyType.TRAFFIC_MISSING, slot.captured.anomalyType)
        assertEquals(50.0, slot.captured.coveragePct)
        assertEquals("traffic-rules-v1", slot.captured.ruleVersion)
    }
}
