package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.traffic.repository.NetworkHourAggregateProjection
import com.dscorp.wispadmin.traffic.repository.NetworkTrafficHourOfDayRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficDailyRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficHourlyRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class NetworkTrafficAnalyticsServiceTest {

    private val networkHourRepository = mockk<NetworkTrafficHourOfDayRepository>()
    private val hourlyRepository = mockk<SubscriptionTrafficHourlyRepository>()
    private val dailyRepository = mockk<SubscriptionTrafficDailyRepository>()

    private val service = NetworkTrafficAnalyticsService(
        networkHourRepository,
        hourlyRepository,
        dailyRepository
    )

    @Test
    fun `getHourlyProfile retorna 24 horas con pico`() {
        every { networkHourRepository.aggregateHourlyProfile(any()) } returns listOf(
            object : NetworkHourAggregateProjection {
                override fun getHourOfDay(): Int = 20
                override fun getRxBytes(): Long = 5000
                override fun getTxBytes(): Long = 500
            }
        )
        every { dailyRepository.aggregateNetworkDailyTrend(any()) } returns emptyList()

        val profile = service.getHourlyProfile(3)

        assertEquals(24, profile.points.size)
        assertEquals(20, profile.peakHour)
        assertNotNull(profile.peakHourLabel)
    }
}
