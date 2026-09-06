package com.dscorp.wispadmin.traffic.scheduled

import com.dscorp.wispadmin.traffic.service.NetworkTrafficRollupService
import com.dscorp.wispadmin.traffic.service.SubscriptionTrafficRetentionService
import com.dscorp.wispadmin.traffic.service.TrafficAggregationJobService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class SubscriptionTrafficMaintenanceSchedulerTest {

    @Test
    fun `nightly maintenance purges expired traffic samples`() {
        val aggregation = mockk<TrafficAggregationJobService>(relaxed = true)
        val rollup = mockk<NetworkTrafficRollupService>(relaxed = true)
        val retention = mockk<SubscriptionTrafficRetentionService>()
        every { retention.purgeExpired() } returns SubscriptionTrafficRetentionService.PurgeResult(1, 0, 0, 0, 0)

        SubscriptionTrafficMaintenanceScheduler(aggregation, rollup, retention).nightlyMaintenance()

        verify { retention.purgeExpired() }
        verify { aggregation.catchUpDaily(any()) }
        verify { rollup.rollupRecentDays(7) }
    }
}
