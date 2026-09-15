package com.dscorp.wispadmin.wispadmin.service.subscription

import com.dscorp.wispadmin.wispadmin.data.model.AccessMigrationStage
import com.dscorp.wispadmin.wispadmin.data.model.SubscriptionAccessMigration
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionAccessMigrationRepository
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class AccessMigrationQuarantineJobTest {

    @Test
    fun `finishes rows whose quarantine has elapsed`() {
        val repository = mockk<SubscriptionAccessMigrationRepository>()
        val service = mockk<AccessMigrationService>(relaxed = true)
        val clock = Clock.fixed(Instant.parse("2026-09-18T08:15:00Z"), ZoneOffset.UTC)
        val due = SubscriptionAccessMigration(
            id = 3,
            subscriptionId = 1001,
            stage = AccessMigrationStage.QUARANTINE,
            quarantineUntil = LocalDateTime.of(2026, 9, 18, 3, 0),
        )
        every {
            repository.findByStageAndQuarantineUntilLessThanEqual(
                AccessMigrationStage.QUARANTINE,
                any(),
            )
        } returns listOf(due)
        val job = AccessMigrationQuarantineJob(repository, service)
        job.clock = clock

        val processed = job.finishDueQuarantines()

        assertEquals(1, processed)
        verify { service.finishQuarantine(due) }
    }

    @Test
    fun `scheduled cron is a no-op when operational jobs are off`() {
        val repository = mockk<SubscriptionAccessMigrationRepository>(relaxed = true)
        val service = mockk<AccessMigrationService>(relaxed = true)
        val job = AccessMigrationQuarantineJob(repository, service, operationalJobsEnabled = false)

        assertEquals(0, job.scheduledFinishDueQuarantines())
        verify { repository wasNot Called }
        verify { service wasNot Called }
    }
}
