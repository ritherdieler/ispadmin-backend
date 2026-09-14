package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.traffic.config.TrafficProperties
import com.dscorp.wispadmin.traffic.entity.TrafficAggregationLayer
import com.dscorp.wispadmin.traffic.entity.TrafficAggregationWatermark
import com.dscorp.wispadmin.traffic.repository.NetworkTrafficHourOfDayRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficDailyRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficFiveMinuteRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficHourlyRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficSampleRepository
import com.dscorp.wispadmin.traffic.repository.TrafficAggregationWatermarkRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.Optional

class SubscriptionTrafficRetentionServiceTest {

    private val sampleRepository = mockk<SubscriptionTrafficSampleRepository>(relaxed = true)
    private val fiveMinuteRepository = mockk<SubscriptionTrafficFiveMinuteRepository>(relaxed = true)
    private val hourlyRepository = mockk<SubscriptionTrafficHourlyRepository>(relaxed = true)
    private val dailyRepository = mockk<SubscriptionTrafficDailyRepository>(relaxed = true)
    private val networkHourRepository = mockk<NetworkTrafficHourOfDayRepository>(relaxed = true)
    private val watermarkRepository = mockk<TrafficAggregationWatermarkRepository>()

    private val service = SubscriptionTrafficRetentionService(
        sampleRepository,
        fiveMinuteRepository,
        hourlyRepository,
        dailyRepository,
        networkHourRepository,
        watermarkRepository,
        TrafficProperties()
    )

    @Test
    fun `purge no elimina RAW si watermark FIVE_MINUTE ausente`() {
        every { watermarkRepository.findById(any()) } returns Optional.empty()

        val result = service.purgeExpired()

        assertEquals(0, result.rawDeleted)
        verify(exactly = 0) { sampleRepository.deleteOlderThan(any()) }
    }

    @Test
    fun `purge elimina RAW solo hasta watermark consolidado`() {
        val consolidated = LocalDateTime.now().minusDays(4)
        every { watermarkRepository.findById(TrafficAggregationLayer.FIVE_MINUTE) } returns Optional.of(
            TrafficAggregationWatermark(TrafficAggregationLayer.FIVE_MINUTE, consolidated)
        )
        every { watermarkRepository.findById(TrafficAggregationLayer.HOURLY) } returns Optional.empty()
        every { watermarkRepository.findById(TrafficAggregationLayer.DAILY) } returns Optional.empty()
        every { sampleRepository.deleteOlderThan(any()) } returns 42

        val result = service.purgeExpired()

        assertEquals(42, result.rawDeleted)
        verify { sampleRepository.deleteOlderThan(match { it == consolidated || it.isBefore(consolidated.plusSeconds(1)) }) }
        verify(exactly = 0) { fiveMinuteRepository.deleteOlderThan(any()) }
    }
}
