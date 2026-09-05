package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.traffic.config.TrafficProperties
import com.dscorp.wispadmin.traffic.entity.TrafficAggregationLayer
import com.dscorp.wispadmin.traffic.entity.TrafficAggregationRun
import com.dscorp.wispadmin.traffic.entity.TrafficAggregationRunStatus
import com.dscorp.wispadmin.traffic.entity.TrafficAggregationWatermark
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficFiveMinuteRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficHourlyRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficSampleRepository
import com.dscorp.wispadmin.traffic.repository.TrafficAggregationRunRepository
import com.dscorp.wispadmin.traffic.repository.TrafficAggregationWatermarkRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.Optional
import java.util.concurrent.atomic.AtomicReference

class TrafficAggregationJobServiceTest {

    private val watermarkRepository = mockk<TrafficAggregationWatermarkRepository>(relaxed = true)
    private val runRepository = mockk<TrafficAggregationRunRepository>(relaxed = true)
    private val sampleRepository = mockk<SubscriptionTrafficSampleRepository>()
    private val fiveMinuteRepository = mockk<SubscriptionTrafficFiveMinuteRepository>()
    private val hourlyRepository = mockk<SubscriptionTrafficHourlyRepository>()
    private val rollupService = mockk<SubscriptionTrafficRollupService>(relaxed = true)
    private val props = TrafficProperties().apply {
        poll.bucketMinutes = 1
        aggregation.catchUpChunkHours = 6
    }

    private fun service() = TrafficAggregationJobService(
        watermarkRepository,
        runRepository,
        sampleRepository,
        fiveMinuteRepository,
        hourlyRepository,
        rollupService,
        props
    )

    @Test
    fun `catchUpFiveMinute recupera ventana omitida y avanza watermark`() {
        val now = LocalDateTime.of(2026, 8, 29, 11, 23, 0)
        val from = LocalDateTime.of(2026, 8, 29, 10, 0, 0)
        every { watermarkRepository.findById(TrafficAggregationLayer.ONE_MINUTE_SINCE) } returns Optional.of(
            TrafficAggregationWatermark(TrafficAggregationLayer.ONE_MINUTE_SINCE, LocalDateTime.of(2026, 8, 29, 0, 0))
        )
        every { watermarkRepository.findById(TrafficAggregationLayer.FIVE_MINUTE) } returns Optional.of(
            TrafficAggregationWatermark(TrafficAggregationLayer.FIVE_MINUTE, from.minusMinutes(5))
        )
        every { sampleRepository.findMinBucketStart() } returns from
        every { runRepository.save(any()) } answers { firstArg() }
        val watermarkSlot = AtomicReference<TrafficAggregationWatermark>()
        every { watermarkRepository.save(any()) } answers {
            val wm = firstArg<TrafficAggregationWatermark>()
            if (wm.layer == TrafficAggregationLayer.FIVE_MINUTE) watermarkSlot.set(wm)
            wm
        }

        service().catchUpFiveMinute(now)

        verify { rollupService.rollupFiveMinute(any(), any(), any()) }
        assertEquals(LocalDateTime.of(2026, 8, 29, 11, 15, 0), watermarkSlot.get().consolidatedThrough)
    }

    @Test
    fun `catchUpFiveMinute reintento no falla y registra run OK`() {
        val now = LocalDateTime.of(2026, 8, 29, 11, 7, 0)
        every { watermarkRepository.findById(TrafficAggregationLayer.ONE_MINUTE_SINCE) } returns Optional.of(
            TrafficAggregationWatermark(TrafficAggregationLayer.ONE_MINUTE_SINCE, LocalDateTime.of(2026, 8, 29, 0, 0))
        )
        every { watermarkRepository.findById(TrafficAggregationLayer.FIVE_MINUTE) } returns Optional.of(
            TrafficAggregationWatermark(TrafficAggregationLayer.FIVE_MINUTE, LocalDateTime.of(2026, 8, 29, 10, 55, 0))
        )
        every { sampleRepository.findMinBucketStart() } returns LocalDateTime.of(2026, 8, 29, 10, 0)
        val runs = mutableListOf<TrafficAggregationRun>()
        every { runRepository.save(any()) } answers {
            val run = firstArg<TrafficAggregationRun>()
            runs.add(run.copy())
            run
        }
        every { watermarkRepository.save(any()) } answers { firstArg() }

        service().catchUpFiveMinute(now)
        every { watermarkRepository.findById(TrafficAggregationLayer.FIVE_MINUTE) } returns Optional.of(
            TrafficAggregationWatermark(TrafficAggregationLayer.FIVE_MINUTE, LocalDateTime.of(2026, 8, 29, 11, 0, 0))
        )
        service().catchUpFiveMinute(now)

        val terminal = runs.filter { it.status != TrafficAggregationRunStatus.RUNNING }
        assertTrue(terminal.any { it.status == TrafficAggregationRunStatus.OK })
        assertTrue(terminal.any { it.status == TrafficAggregationRunStatus.SKIPPED })
    }
}
