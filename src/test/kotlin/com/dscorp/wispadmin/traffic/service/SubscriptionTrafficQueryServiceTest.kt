package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficDaily
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficSample
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficDailyRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficHourlyRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficMonthlyRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficSampleRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SubscriptionTrafficQueryServiceTest {

    private val sampleRepository = mockk<SubscriptionTrafficSampleRepository>()
    private val hourlyRepository = mockk<SubscriptionTrafficHourlyRepository>()
    private val dailyRepository = mockk<SubscriptionTrafficDailyRepository>()
    private val monthlyRepository = mockk<SubscriptionTrafficMonthlyRepository>()

    private val service = SubscriptionTrafficQueryService(
        sampleRepository,
        hourlyRepository,
        dailyRepository,
        monthlyRepository
    )

    @Test
    fun `getSeries retorna puntos sample`() {
        val bucket = LocalDateTime.of(2026, 8, 27, 10, 0)
        every {
            sampleRepository.findBySubscriptionIdAndBucketStartBetweenOrderByBucketStartAsc(1, any(), any())
        } returns listOf(
            SubscriptionTrafficSample(
                subscriptionId = 1,
                hostDeviceId = 2,
                bucketStart = bucket,
                rxBytesDelta = 1000,
                txBytesDelta = 500,
                avgMbpsDown = 1.2,
                avgMbpsUp = 0.6
            )
        )

        val series = service.getSeries(1, "sample", null, null)

        assertNotNull(series)
        assertEquals(1, series!!.points.size)
        assertEquals(1000, series.points.first().rxBytes)
    }

    @Test
    fun `getSeries by subscription no mezcla samples de otra suscripcion tras reasignar IP`() {
        val t1 = LocalDateTime.of(2026, 8, 20, 10, 0)
        val t2 = LocalDateTime.of(2026, 8, 21, 10, 0)
        every {
            sampleRepository.findBySubscriptionIdAndBucketStartBetweenOrderByBucketStartAsc(10, any(), any())
        } returns listOf(
            SubscriptionTrafficSample(
                clientIp = "192.168.250.20",
                subscriptionId = 10,
                hostDeviceId = 8,
                bucketStart = t1,
                rxBytesDelta = 111,
                txBytesDelta = 10,
                avgMbpsDown = 1.0,
                avgMbpsUp = 0.1,
            )
        )

        val series = service.getSeries(10, "sample", t1.minusHours(1), t2.plusHours(1))

        assertEquals(1, series!!.points.size)
        assertEquals(111, series.points.single().rxBytes)
    }

    @Test
    fun `getLatest incluye ip y sampleStatus`() {
        every { sampleRepository.findTopBySubscriptionIdOrderByBucketStartDesc(2360) } returns SubscriptionTrafficSample(
            id = 9L,
            clientIp = "192.168.250.20",
            subscriptionId = 2360,
            hostDeviceId = 8,
            sampleStatus = com.dscorp.wispadmin.traffic.entity.TrafficSampleStatus.OK,
            avgMbpsDown = 12.0,
            queueId = "q-1",
        )

        val latest = service.getLatest(2360)

        assertEquals(2360, latest.subscriptionId)
        assertEquals("192.168.250.20", latest.ip)
        assertEquals("OK", latest.sampleStatus)
        assertEquals("q-1", latest.queueId)
    }

    @Test
    fun `getSeries retorna los ultimos puntos sample cuando hay mas de MAX_POINTS`() {
        val samples = (0 until 600).map { index ->
            SubscriptionTrafficSample(
                subscriptionId = 1,
                hostDeviceId = 2,
                bucketStart = LocalDateTime.of(2026, 8, 20, 0, 0).plusMinutes(index * 5L),
                rxBytesDelta = index.toLong(),
                txBytesDelta = 100,
                avgMbpsDown = 1.0,
                avgMbpsUp = 0.5
            )
        }
        every {
            sampleRepository.findBySubscriptionIdAndBucketStartBetweenOrderByBucketStartAsc(1, any(), any())
        } returns samples

        val series = service.getSeries(1, "sample", null, LocalDateTime.of(2026, 8, 28, 0, 0))

        assertNotNull(series)
        assertEquals(500, series!!.points.size)
        assertEquals(100, series.points.first().rxBytes)
        assertEquals(599, series.points.last().rxBytes)
    }

    @Test
    fun `getSummary agrega desde daily si no hay monthly`() {
        every { monthlyRepository.findBySubscriptionIdAndYearMonth(1, "2026-08") } returns null
        every {
            dailyRepository.findBySubscriptionIdAndBucketStartBetweenOrderByBucketStartAsc(1, any(), any())
        } returns listOf(
            SubscriptionTrafficDaily(
                subscriptionId = 1,
                bucketStart = java.time.LocalDate.of(2026, 8, 1),
                rxBytesTotal = 1_000_000_000,
                txBytesTotal = 500_000_000,
                maxMbpsDown = 50.0,
                maxMbpsUp = 20.0,
                p95MbpsDown = 40.0,
                p95MbpsUp = 15.0,
                activeHours = 10
            )
        )

        val summary = service.getSummary(1, "2026-08")

        assertNotNull(summary)
        assertEquals(1_000_000_000, summary.rxBytesTotal)
    }

    @Test
    fun `getSummary returns zeros when there is no monthly or daily rollup`() {
        every { monthlyRepository.findBySubscriptionIdAndYearMonth(2360, "2026-09") } returns null
        every {
            dailyRepository.findBySubscriptionIdAndBucketStartBetweenOrderByBucketStartAsc(2360, any(), any())
        } returns emptyList()

        val summary = service.getSummary(2360, "2026-09")

        assertEquals(2360, summary.subscriptionId)
        assertEquals("2026-09", summary.yearMonth)
        assertEquals(0, summary.rxBytesTotal)
        assertEquals(0, summary.txBytesTotal)
        assertEquals(0.0, summary.rxGbTotal)
        assertEquals(0, summary.activeDays)
    }

    @Test
    fun `getDay agrega samples por hora`() {
        val day = java.time.LocalDate.of(2026, 8, 27)
        every {
            sampleRepository.findBySubscriptionIdAndBucketStartBetweenOrderByBucketStartAsc(1, any(), any())
        } returns listOf(
            SubscriptionTrafficSample(
                subscriptionId = 1,
                hostDeviceId = 2,
                bucketStart = LocalDateTime.of(2026, 8, 27, 20, 0),
                rxBytesDelta = 1000,
                txBytesDelta = 100,
                avgMbpsDown = 1.0,
                avgMbpsUp = 0.5
            ),
            SubscriptionTrafficSample(
                subscriptionId = 1,
                hostDeviceId = 2,
                bucketStart = LocalDateTime.of(2026, 8, 27, 20, 5),
                rxBytesDelta = 2000,
                txBytesDelta = 200,
                avgMbpsDown = 2.0,
                avgMbpsUp = 1.0
            )
        )

        val dayView = service.getDay(1, day)

        assertNotNull(dayView)
        assertEquals(1, dayView!!.points.size)
        assertEquals(3000, dayView.points.first().rxBytes)
        assertEquals(20, dayView.peakHour)
    }
}
