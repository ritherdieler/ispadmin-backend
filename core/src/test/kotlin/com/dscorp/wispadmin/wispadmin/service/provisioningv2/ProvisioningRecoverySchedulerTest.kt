package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ProvisioningRecoverySchedulerTest {
    @Test fun `continues recovering due operations after one operation fails`() {
        val journal = mockk<ProvisioningJournal>()
        val executor = mockk<ProvisioningExecutor>(relaxed = true)
        every { journal.due("staging", any()) } returns listOf("broken", "resumable")
        every { executor.advance("staging", "broken") } throws IllegalStateException("unexpected")
        val scheduler = ProvisioningRecoveryScheduler(journal, executor,
            Clock.fixed(Instant.parse("2026-09-23T00:00:00Z"), ZoneOffset.UTC), "staging")

        scheduler.recover()

        verify(exactly = 1) { executor.advance("staging", "broken") }
        verify(exactly = 1) { executor.advance("staging", "resumable") }
    }
}
