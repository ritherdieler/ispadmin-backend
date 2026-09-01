package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.domain.WifiAggregationWatermark
import com.dscorp.wispadmin.servicehealth.repository.HealthCursorRepository
import com.dscorp.wispadmin.servicehealth.repository.WifiAggregationWatermarkRepository
import com.dscorp.wispadmin.servicehealth.service.HealthLifecycleService
import com.dscorp.wispadmin.servicehealth.service.WifiStationRollupService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.Optional
import javax.persistence.EntityManager
import javax.persistence.Query

class HealthLifecycleServiceTest {
    private val cursors = mockk<HealthCursorRepository>(relaxed = true)
    private val em = mockk<EntityManager>(relaxed = true)
    private val watermarks = mockk<WifiAggregationWatermarkRepository>()
    private val rollup = mockk<WifiStationRollupService>(relaxed = true)
    private val query = mockk<Query>(relaxed = true)
    private val properties = ServiceHealthProperties().apply { enabled = true }
    private val service = HealthLifecycleService(properties, cursors, em, watermarks, rollup)

    @Test
    fun `purge does not delete raw stations without hourly watermark`() {
        every { watermarks.findById("HOURLY") } returns Optional.empty()
        every { em.createQuery(any<String>()) } returns query

        service.purge()

        verify { rollup.catchUp(any()) }
        verify(exactly = 0) { em.createQuery(match<String> { it.contains("WifiStationSample") }) }
        verify { em.createQuery(match<String> { it.contains("WifiStationHourly") }) }
    }

    @Test
    fun `purge deletes raw stations only up to watermark`() {
        val watermark = Instant.now().minus(Duration.ofDays(20))
        every { watermarks.findById("HOURLY") } returns Optional.of(
            WifiAggregationWatermark(layer = "HOURLY", consolidatedThrough = watermark),
        )
        every { em.createQuery(any<String>()) } returns query

        service.purge()

        verify { em.createQuery(match<String> { it.contains("WifiStationSample") }) }
        verify { query.setParameter("cutoff", watermark) }
    }
}
