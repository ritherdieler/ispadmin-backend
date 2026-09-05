package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.traffic.entity.TrafficAnomalyStatus
import com.dscorp.wispadmin.traffic.dto.BandwidthNetworkDto
import com.dscorp.wispadmin.traffic.port.TrafficDirectoryPort
import com.dscorp.wispadmin.traffic.port.TrafficDirectoryTarget
import com.dscorp.wispadmin.traffic.repository.BandwidthNetworkBucketProjection
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficDailyRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficFiveMinuteRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficHourlyRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficSampleRepository
import com.dscorp.wispadmin.traffic.repository.TrafficAnomalyEventRepository
import com.dscorp.wispadmin.traffic.repository.TrafficRouterRepository
import com.dscorp.wispadmin.traffic.repository.TrafficSourceRunRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class BandwidthIntelligenceServiceTest {

    private val directory = mockk<TrafficDirectoryPort>()
    private val routerRepository = mockk<TrafficRouterRepository>(relaxed = true)
    private val sampleRepository = mockk<SubscriptionTrafficSampleRepository>(relaxed = true)
    private val fiveMinuteRepository = mockk<SubscriptionTrafficFiveMinuteRepository>()
    private val hourlyRepository = mockk<SubscriptionTrafficHourlyRepository>(relaxed = true)
    private val dailyRepository = mockk<SubscriptionTrafficDailyRepository>(relaxed = true)
    private val sourceRunRepository = mockk<TrafficSourceRunRepository>(relaxed = true)
    private val anomalyRepository = mockk<TrafficAnomalyEventRepository>()
    private val aggregationJobService = mockk<TrafficAggregationJobService>(relaxed = true)

    private val service = BandwidthIntelligenceService(
        directory,
        routerRepository,
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

        val series = service.series(from, to, "5m", null, null)

        assertEquals(1, series.points.size)
        assertEquals(1000, series.points.first().rxBytes)
        assertEquals(40.0, series.points.first().avgMbpsDown)
        verify(exactly = 1) { fiveMinuteRepository.aggregateNetworkBuckets(from, to) }
        verify(exactly = 0) { fiveMinuteRepository.findInBucketRange(any(), any()) }
        verify(exactly = 0) { directory.list() }
    }

    @Test
    fun `series de red con router filtra por host en SQL`() {
        every { fiveMinuteRepository.aggregateNetworkBucketsByHost(from, to, 7) } returns emptyList()

        service.series(from, to, "5m", routerId = 7, planId = null)

        verify(exactly = 1) { fiveMinuteRepository.aggregateNetworkBucketsByHost(from, to, 7) }
        verify(exactly = 0) { fiveMinuteRepository.findInBucketRange(any(), any()) }
        verify(exactly = 0) { directory.list() }
    }

    @Test
    fun `series de red en 24 horas usa agregacion horaria`() {
        every { hourlyRepository.aggregateNetworkBuckets(from, to) } returns emptyList()

        val series = service.series(from, to, "auto", routerId = null, planId = null)

        assertEquals("1h", series.meta.resolution)
        verify(exactly = 1) { hourlyRepository.aggregateNetworkBuckets(from, to) }
        verify(exactly = 0) { fiveMinuteRepository.aggregateNetworkBuckets(any(), any()) }
    }

    @Test
    fun `series de red en 30 dias usa agregacion diaria`() {
        val monthFrom = LocalDateTime.of(2026, 8, 1, 10, 0)
        val monthTo = monthFrom.plusDays(30)
        every { dailyRepository.aggregateNetworkBuckets(monthFrom.toLocalDate(), monthTo.toLocalDate().plusDays(1)) } returns emptyList()

        val series = service.series(monthFrom, monthTo, "auto", routerId = null, planId = null)

        assertEquals("1d", series.meta.resolution)
        verify(exactly = 1) { dailyRepository.aggregateNetworkBuckets(monthFrom.toLocalDate(), monthTo.toLocalDate().plusDays(1)) }
        verify(exactly = 0) { hourlyRepository.aggregateNetworkBuckets(any(), any()) }
    }

    @Test
    fun `network devuelve overview y serie con una sola agregacion`() {
        every { hourlyRepository.aggregateNetworkBuckets(from, to) } returns listOf(
            bucket(from, rx = 1000, tx = 100, down = 20.0, up = 3.0, coverage = 100.0)
        )
        every { hourlyRepository.countDistinctSubscriptions(from, to) } returns 1
        every { directory.list() } returns listOf(target(1))
        every { anomalyRepository.countByEventStatus(TrafficAnomalyStatus.OPEN) } returns 0

        val snapshot: BandwidthNetworkDto = service.network(from, to, "auto", null, null)

        assertEquals(1, snapshot.series.points.size)
        assertEquals(1000, snapshot.overview.totalRxBytes)
        assertEquals(1, snapshot.overview.activeSubscriptions)
        verify(exactly = 1) { hourlyRepository.aggregateNetworkBuckets(from, to) }
    }

    @Test
    fun `ranking de clientes en 24 horas usa datos consolidados de 5 minutos`() {
        every { directory.list() } returns listOf(target(1))
        every { fiveMinuteRepository.findBySubscriptionIdAndBucketStartBetweenOrderByBucketStartAsc(1, from, to) } returns emptyList()

        service.subscriptions(from, to, null, null, null, "consumption", 0, 25)

        verify(exactly = 1) { fiveMinuteRepository.findBySubscriptionIdAndBucketStartBetweenOrderByBucketStartAsc(1, from, to) }
        verify(exactly = 0) { sampleRepository.findAllInBucketRange(any(), any()) }
    }

    @Test
    fun `ranking de clientes en rangos mayores a 7 dias usa datos diarios`() {
        val monthFrom = LocalDateTime.of(2026, 8, 1, 10, 0)
        val monthTo = monthFrom.plusDays(30)
        every { directory.list() } returns listOf(target(1))
        every { dailyRepository.findBySubscriptionIdAndBucketStartBetweenOrderByBucketStartAsc(1, monthFrom.toLocalDate(), monthTo.toLocalDate().plusDays(1)) } returns emptyList()

        service.subscriptions(monthFrom, monthTo, null, null, null, "consumption", 0, 25)

        verify(exactly = 1) { dailyRepository.findBySubscriptionIdAndBucketStartBetweenOrderByBucketStartAsc(1, monthFrom.toLocalDate(), monthTo.toLocalDate().plusDays(1)) }
        verify(exactly = 0) { hourlyRepository.findBySubscriptionIdAndBucketStartBetweenOrderByBucketStartAsc(any(), any(), any()) }
    }

    @Test
    fun `series de red con plan agrega solo ids elegibles`() {
        every { directory.list() } returns listOf(
            target(1, routerId = 1, planId = 9, download = 100),
            target(2, routerId = 1, planId = 3, download = 50)
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
        every { directory.list() } returns listOf(
            target(1, download = 100),
            target(2, download = 50)
        )
        every { anomalyRepository.countByEventStatus(TrafficAnomalyStatus.OPEN) } returns 3

        val overview = service.overview(from, to, "5m", null, null)

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
        every { directory.list() } returns listOf(
            target(1, download = 100),
            target(2, download = 50)
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

    private fun target(
        id: Int,
        routerId: Int = 1,
        planId: Int = 1,
        download: Int = 100
    ) = TrafficDirectoryTarget(
        subscriptionId = id,
        ip = "10.0.0.$id",
        routerHint = routerId,
        planId = planId,
        planName = "P$planId",
        planDownloadMbps = download,
        planUploadMbps = download / 2,
        displayName = "C$id",
    )
}
