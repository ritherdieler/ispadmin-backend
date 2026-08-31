package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.traffic.entity.TrafficAnomalyStatus
import com.dscorp.wispadmin.traffic.repository.BandwidthNetworkBucketProjection
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficDailyRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficFiveMinuteRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficHourlyRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficSampleRepository
import com.dscorp.wispadmin.traffic.repository.TrafficAnomalyEventRepository
import com.dscorp.wispadmin.traffic.repository.TrafficSourceRunRepository
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class BandwidthIntelligenceServiceTest {

    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val sampleRepository = mockk<SubscriptionTrafficSampleRepository>(relaxed = true)
    private val fiveMinuteRepository = mockk<SubscriptionTrafficFiveMinuteRepository>()
    private val hourlyRepository = mockk<SubscriptionTrafficHourlyRepository>(relaxed = true)
    private val dailyRepository = mockk<SubscriptionTrafficDailyRepository>(relaxed = true)
    private val sourceRunRepository = mockk<TrafficSourceRunRepository>(relaxed = true)
    private val anomalyRepository = mockk<TrafficAnomalyEventRepository>()
    private val aggregationJobService = mockk<TrafficAggregationJobService>(relaxed = true)

    private val service = BandwidthIntelligenceService(
        subscriptionRepository,
        sampleRepository,
        fiveMinuteRepository,
        hourlyRepository,
        dailyRepository,
        sourceRunRepository,
        anomalyRepository,
        aggregationJobService
    )

    private val from = LocalDateTime.of(2026, 8, 30, 10, 0)
    private val to = LocalDateTime.of(2026, 8, 31, 10, 0)

    @Test
    fun `series de red agrega en SQL y no carga filas crudas de 5m`() {
        every { fiveMinuteRepository.aggregateNetworkBuckets(from, to) } returns listOf(
            bucket(from, rx = 1000, tx = 200, down = 40.0, up = 5.0, coverage = 90.0)
        )

        val series = service.series(from, to, "auto", null, null)

        assertEquals(1, series.points.size)
        assertEquals(1000, series.points.first().rxBytes)
        assertEquals(40.0, series.points.first().avgMbpsDown)
        verify(exactly = 1) { fiveMinuteRepository.aggregateNetworkBuckets(from, to) }
        verify(exactly = 0) { fiveMinuteRepository.findInBucketRange(any(), any()) }
        verify(exactly = 0) { subscriptionRepository.findForTrafficPolling() }
    }

    @Test
    fun `series de red con router filtra por host en SQL`() {
        every { fiveMinuteRepository.aggregateNetworkBucketsByHost(from, to, 7) } returns emptyList()

        service.series(from, to, "5m", routerId = 7, planId = null)

        verify(exactly = 1) { fiveMinuteRepository.aggregateNetworkBucketsByHost(from, to, 7) }
        verify(exactly = 0) { fiveMinuteRepository.findInBucketRange(any(), any()) }
        verify(exactly = 0) { subscriptionRepository.findForTrafficPolling() }
    }

    @Test
    fun `series de red con plan agrega solo ids elegibles`() {
        every { subscriptionRepository.findForTrafficPolling() } returns listOf(
            subscription(1, routerId = 1, planId = 9, download = 100),
            subscription(2, routerId = 1, planId = 3, download = 50)
        )
        every {
            fiveMinuteRepository.aggregateNetworkBucketsBySubscriptions(from, to, setOf(1))
        } returns emptyList()

        service.series(from, to, "5m", routerId = null, planId = 9)

        verify(exactly = 1) {
            fiveMinuteRepository.aggregateNetworkBucketsBySubscriptions(from, to, setOf(1))
        }
        verify(exactly = 0) { fiveMinuteRepository.findInBucketRange(any(), any()) }
    }

    @Test
    fun `overview de red usa agregacion SQL y count de anomalias abiertas`() {
        every { fiveMinuteRepository.aggregateNetworkBuckets(from, to) } returns listOf(
            bucket(from, rx = 1000, tx = 200, down = 40.0, up = 5.0, coverage = 80.0),
            bucket(from.plusMinutes(5), rx = 2000, tx = 400, down = 80.0, up = 10.0, coverage = 100.0)
        )
        every { fiveMinuteRepository.countDistinctSubscriptions(from, to) } returns 12
        every { subscriptionRepository.findForTrafficPolling() } returns listOf(
            subscription(1, download = 100),
            subscription(2, download = 50)
        )
        every { anomalyRepository.countByEventStatus(TrafficAnomalyStatus.OPEN) } returns 3

        val overview = service.overview(from, to, "auto", null, null)

        assertEquals(3000, overview.totalRxBytes)
        assertEquals(80.0, overview.peakMbpsDown)
        assertEquals(12, overview.activeSubscriptions)
        assertEquals(3, overview.openAnomalies)
        assertTrue(overview.utilizationPct != null)
        verify(exactly = 0) { fiveMinuteRepository.findInBucketRange(any(), any()) }
        verify(exactly = 0) { anomalyRepository.findByEventStatusOrderByStartedAtDesc(any()) }
    }

    @Test
    fun `subscriptions con varias ids no hace findInBucketRange global`() {
        val customerFrom = LocalDateTime.of(2026, 8, 29, 10, 0)
        val customerTo = LocalDateTime.of(2026, 8, 31, 10, 0)
        every { subscriptionRepository.findForTrafficPolling() } returns listOf(
            subscription(1, download = 100),
            subscription(2, download = 50)
        )
        every {
            fiveMinuteRepository.findInBucketRangeForSubscriptions(customerFrom, customerTo, setOf(1, 2))
        } returns emptyList()

        val page = service.subscriptions(customerFrom, customerTo, null, null, null, "consumption", 0, 25)

        assertEquals(2, page.total)
        verify(exactly = 1) {
            fiveMinuteRepository.findInBucketRangeForSubscriptions(customerFrom, customerTo, setOf(1, 2))
        }
        verify(exactly = 0) { fiveMinuteRepository.findInBucketRange(any(), any()) }
    }

    @Test
    fun `anomalyPage no usa findAll`() {
        every {
            anomalyRepository.findFiltered(null, null, null, null)
        } returns emptyList()

        val page = service.anomalyPage(null, null, null, null, 0, 25)

        assertEquals(0, page.total)
        verify(exactly = 1) { anomalyRepository.findFiltered(null, null, null, null) }
        verify(exactly = 0) { anomalyRepository.findAll() }
    }

    private fun bucket(
        start: LocalDateTime,
        rx: Long,
        tx: Long,
        down: Double,
        up: Double,
        coverage: Double
    ) = object : BandwidthNetworkBucketProjection {
        override fun getBucketStart(): LocalDateTime = start
        override fun getRxBytes(): Long = rx
        override fun getTxBytes(): Long = tx
        override fun getAvgMbpsDown(): Double = down
        override fun getAvgMbpsUp(): Double = up
        override fun getP95MbpsDown(): Double = down
        override fun getP95MbpsUp(): Double = up
        override fun getCoveragePct(): Double = coverage
    }

    private fun subscription(
        id: Int,
        routerId: Int = 1,
        planId: Int = 1,
        download: Int = 100
    ) = Subscription(
        id = id,
        ip = "10.0.0.$id",
        hostDevice = NetworkDevice(id = routerId, name = "R$routerId"),
        plan = Plan(id = planId, name = "P$planId", downloadSpeed = download, uploadSpeed = download / 2),
        equipmentCondition = EquipmentCondition.LOAN
    )
}
