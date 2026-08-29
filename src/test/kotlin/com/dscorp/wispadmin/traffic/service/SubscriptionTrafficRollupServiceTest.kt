package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.traffic.config.TrafficProperties
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficFiveMinute
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficHourly
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficSample
import com.dscorp.wispadmin.traffic.entity.TrafficSampleStatus
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficDailyRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficFiveMinuteRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficHourlyRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficMonthlyRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficSampleRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SubscriptionTrafficRollupServiceTest {

    private val sampleRepository = mockk<SubscriptionTrafficSampleRepository>()
    private val fiveMinuteRepository = mockk<SubscriptionTrafficFiveMinuteRepository>(relaxed = true)
    private val hourlyRepository = mockk<SubscriptionTrafficHourlyRepository>(relaxed = true)
    private val dailyRepository = mockk<SubscriptionTrafficDailyRepository>(relaxed = true)
    private val monthlyRepository = mockk<SubscriptionTrafficMonthlyRepository>(relaxed = true)

    private fun service(props: TrafficProperties = TrafficProperties()) = SubscriptionTrafficRollupService(
        sampleRepository,
        fiveMinuteRepository,
        hourlyRepository,
        dailyRepository,
        monthlyRepository,
        props
    )

    @Test
    fun `rollupFiveMinute con 5 samples 1m produce cobertura 100 y metricas`() {
        val bucket = LocalDateTime.of(2026, 8, 29, 11, 0, 0)
        val samples = (0 until 5).map { i ->
            SubscriptionTrafficSample(
                subscriptionId = 10,
                hostDeviceId = 8,
                bucketStart = bucket.plusMinutes(i.toLong()),
                intervalSeconds = 60,
                sampleStatus = TrafficSampleStatus.OK,
                rxBytesDelta = 1_000_000L,
                txBytesDelta = 100_000L,
                avgMbpsDown = 85.0 + i,
                avgMbpsUp = 1.0,
                planDownloadMbps = 100,
                planUploadMbps = 100
            )
        }
        every { sampleRepository.findAllInBucketRange(any(), any()) } returns samples
        every { fiveMinuteRepository.findInBucketRange(any(), any()) } returns emptyList()
        val saved = slot<List<SubscriptionTrafficFiveMinute>>()
        every { fiveMinuteRepository.saveAll(capture(saved)) } answers { firstArg() }

        service(
            TrafficProperties(
                poll = TrafficProperties.PollProperties(bucketMinutes = 1),
                aggregation = TrafficProperties.AggregationProperties(
                    oneMinuteSince = "2026-08-29T00:00:00"
                )
            )
        ).rollupFiveMinute(bucket, bucket.plusMinutes(5))

        val row = saved.captured.single()
        assertEquals(5, row.sampleCount)
        assertEquals(5, row.expectedSampleCount)
        assertEquals(100.0, row.coveragePct)
        assertEquals(5_000_000L, row.rxBytesTotal)
        assertEquals(500_000L, row.txBytesTotal)
        assertEquals(89.0, row.maxMbpsDown)
        assertTrue(row.avgMbpsDown > 0.0)
        assertEquals(300, row.secondsOver80)
    }

    @Test
    fun `rollupFiveMinute legacy pre-cutover espera 1 sample`() {
        val bucket = LocalDateTime.of(2026, 8, 28, 10, 0, 0)
        val sample = SubscriptionTrafficSample(
            subscriptionId = 10,
            hostDeviceId = 8,
            bucketStart = bucket,
            intervalSeconds = 300,
            sampleStatus = TrafficSampleStatus.OK,
            rxBytesDelta = 2_000_000L,
            txBytesDelta = 200_000L,
            avgMbpsDown = 20.0,
            avgMbpsUp = 2.0,
            planDownloadMbps = 200
        )
        every { sampleRepository.findAllInBucketRange(any(), any()) } returns listOf(sample)
        every { fiveMinuteRepository.findInBucketRange(any(), any()) } returns emptyList()
        val saved = slot<List<SubscriptionTrafficFiveMinute>>()
        every { fiveMinuteRepository.saveAll(capture(saved)) } answers { firstArg() }

        service(
            TrafficProperties(
                poll = TrafficProperties.PollProperties(bucketMinutes = 1),
                aggregation = TrafficProperties.AggregationProperties(
                    oneMinuteSince = "2026-08-29T00:00:00"
                )
            )
        ).rollupFiveMinute(bucket, bucket.plusMinutes(5))

        val row = saved.captured.single()
        assertEquals(1, row.sampleCount)
        assertEquals(1, row.expectedSampleCount)
        assertEquals(100.0, row.coveragePct)
    }

    @Test
    fun `rollupHourly agrega desde five_minute no desde RAW`() {
        val hour = LocalDateTime.of(2026, 8, 29, 11, 0, 0)
        val rows = (0 until 12).map { i ->
            SubscriptionTrafficFiveMinute(
                subscriptionId = 10,
                hostDeviceId = 8,
                bucketStart = hour.plusMinutes(i * 5L),
                rxBytesTotal = 1_000L,
                txBytesTotal = 100L,
                avgMbpsDown = 5.0 + i,
                avgMbpsUp = 1.0,
                maxMbpsDown = 10.0 + i,
                maxMbpsUp = 2.0,
                p95MbpsDown = 8.0,
                p95MbpsUp = 1.5,
                sampleCount = 5,
                expectedSampleCount = 5,
                coveragePct = 100.0,
                planDownloadMbps = 100,
                secondsOver80 = 30,
                secondsOver90 = 10,
                secondsOver95 = 0
            )
        }
        every { fiveMinuteRepository.findInBucketRange(hour, hour.plusHours(1)) } returns rows
        every { hourlyRepository.findInBucketRange(hour, hour.plusHours(1)) } returns emptyList()
        val saved = slot<List<SubscriptionTrafficHourly>>()
        every { hourlyRepository.saveAll(capture(saved)) } answers { firstArg() }

        service().rollupHourly(hour, hour.plusHours(1))

        verify(exactly = 0) { sampleRepository.findAllInBucketRange(any(), any()) }
        val hourly = saved.captured.single()
        assertEquals(12_000L, hourly.rxBytesTotal)
        assertEquals(60, hourly.sampleCount)
        assertEquals(12, hourly.expectedSampleCount)
        assertEquals(100.0, hourly.coveragePct)
        assertEquals(360, hourly.secondsOver80)
        assertEquals(21.0, hourly.maxMbpsDown)
    }
}
