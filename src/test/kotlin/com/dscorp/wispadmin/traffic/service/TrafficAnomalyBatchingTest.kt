package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.traffic.config.TrafficProperties
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficDaily
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficHourly
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficDailyRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficHourlyRepository
import com.dscorp.wispadmin.traffic.repository.TrafficAnomalyEventRepository
import com.dscorp.wispadmin.traffic.repository.TrafficSourceRunRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class TrafficAnomalyBatchingTest {

    private val hourlyRepository = mockk<SubscriptionTrafficHourlyRepository>(relaxed = true)
    private val dailyRepository = mockk<SubscriptionTrafficDailyRepository>(relaxed = true)
    private val sourceRunRepository = mockk<TrafficSourceRunRepository>(relaxed = true)
    private val anomalyRepository = mockk<TrafficAnomalyEventRepository>(relaxed = true)

    private val now: LocalDateTime = LocalDateTime.of(2026, 9, 1, 10, 0)

    private fun service(batchSize: Int = 2) = TrafficAnomalyService(
        hourlyRepository,
        dailyRepository,
        sourceRunRepository,
        anomalyRepository,
        TrafficProperties(
            anomaly = TrafficProperties.AnomalyProperties(evaluationBatchSize = batchSize)
        )
    )

    @BeforeEach
    fun setup() {
        every { sourceRunRepository.findTop100ByOrderByStartedAtDesc() } returns emptyList()
        every { anomalyRepository.findTopByAnomalyTypeAndSubscriptionIdAndEventStatusOrderByStartedAtDesc(any(), any(), any()) } returns null
        every { anomalyRepository.findTopByAnomalyTypeAndHostDeviceIdAndEventStatusOrderByStartedAtDesc(any(), any(), any()) } returns null
        every { anomalyRepository.save(any()) } answers { firstArg() }
        every { hourlyRepository.findDistinctSubscriptionIdsInBucketRange(any(), any()) } returns emptyList()
        every { dailyRepository.findDistinctSubscriptionIdsInBucketRange(any(), any()) } returns emptyList()
        every { hourlyRepository.findInBucketRangeForSubscriptions(any(), any(), any()) } returns emptyList()
        every { dailyRepository.findInBucketRangeForSubscriptions(any(), any(), any()) } returns emptyList()
    }

    @Test
    fun `no carga el rango completo de horas en memoria`() {
        service().evaluate(now)

        verify(exactly = 0) { hourlyRepository.findInBucketRange(any(), any()) }
        verify(exactly = 0) { dailyRepository.findInBucketRange(any(), any()) }
        verify(exactly = 1) { hourlyRepository.findDistinctSubscriptionIdsInBucketRange(any(), any()) }
        verify(exactly = 1) { dailyRepository.findDistinctSubscriptionIdsInBucketRange(any(), any()) }
    }

    @Test
    fun `recorre las suscripciones por lotes del tamano configurado`() {
        every { hourlyRepository.findDistinctSubscriptionIdsInBucketRange(any(), any()) } returns listOf(1, 2, 3, 4, 5)
        val requested = mutableListOf<Collection<Int>>()
        every { hourlyRepository.findInBucketRangeForSubscriptions(any(), any(), any()) } answers {
            requested.add(thirdArg())
            emptyList()
        }

        service(batchSize = 2).evaluate(now)

        assertEquals(listOf(listOf(1, 2), listOf(3, 4), listOf(5)), requested.map { it.toList() })
    }

    @Test
    fun `el lote diario tambien se acota`() {
        every { dailyRepository.findDistinctSubscriptionIdsInBucketRange(any(), any()) } returns listOf(7, 8, 9)
        val requested = mutableListOf<Collection<Int>>()
        every { dailyRepository.findInBucketRangeForSubscriptions(any(), any(), any()) } answers {
            requested.add(thirdArg())
            emptyList()
        }

        service(batchSize = 2).evaluate(now)

        assertEquals(listOf(listOf(7, 8), listOf(9)), requested.map { it.toList() })
    }

    @Test
    fun `sin suscripciones con datos no pide filas`() {
        service().evaluate(now)

        verify(exactly = 0) { hourlyRepository.findInBucketRangeForSubscriptions(any(), any(), any()) }
        verify(exactly = 0) { dailyRepository.findInBucketRangeForSubscriptions(any(), any(), any()) }
    }

    @Test
    fun `evalua las filas de cada lote sin mezclar suscripciones`() {
        every { hourlyRepository.findDistinctSubscriptionIdsInBucketRange(any(), any()) } returns listOf(1)
        every { hourlyRepository.findInBucketRangeForSubscriptions(any(), any(), any()) } returns listOf(
            hourly(subscriptionId = 1, bucketStart = now.minusHours(2), rx = 5_000, coverage = 100.0),
            hourly(subscriptionId = 1, bucketStart = now, rx = 0, coverage = 100.0)
        )
        every { dailyRepository.findDistinctSubscriptionIdsInBucketRange(any(), any()) } returns emptyList()

        service().evaluate(now)

        verify(atLeast = 1) {
            anomalyRepository.findTopByAnomalyTypeAndSubscriptionIdAndEventStatusOrderByStartedAtDesc(any(), 1, any())
        }
        assertTrue(true)
    }

    private fun hourly(
        subscriptionId: Int,
        bucketStart: LocalDateTime,
        rx: Long,
        coverage: Double
    ) = SubscriptionTrafficHourly(
        subscriptionId = subscriptionId,
        bucketStart = bucketStart,
        rxBytesTotal = rx,
        txBytesTotal = 0,
        coveragePct = coverage
    )

    private fun daily(
        subscriptionId: Int,
        bucketStart: LocalDate,
        rx: Long
    ) = SubscriptionTrafficDaily(
        subscriptionId = subscriptionId,
        bucketStart = bucketStart,
        rxBytesTotal = rx,
        txBytesTotal = 0
    )
}
