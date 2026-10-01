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

    @Test fun `a slow operation does not block the others when a worker pool is used`() {
        val journal = mockk<ProvisioningJournal>()
        val executor = mockk<ProvisioningExecutor>(relaxed = true)
        val slowStarted = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val fastDone = java.util.concurrent.CountDownLatch(1)
        every { journal.due("staging", any()) } returns listOf("slow", "fast")
        every { executor.advance("staging", "slow") } answers { slowStarted.countDown(); release.await(); Unit }
        every { executor.advance("staging", "fast") } answers { fastDone.countDown(); Unit }
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        val scheduler = ProvisioningRecoveryScheduler(journal, executor,
            Clock.fixed(Instant.parse("2026-09-23T00:00:00Z"), ZoneOffset.UTC), "staging", workers = pool)

        val run = Thread { scheduler.recover() }.apply { start() }

        org.junit.jupiter.api.Assertions.assertTrue(slowStarted.await(2, java.util.concurrent.TimeUnit.SECONDS))
        org.junit.jupiter.api.Assertions.assertTrue(fastDone.await(2, java.util.concurrent.TimeUnit.SECONDS))
        release.countDown()
        run.join(2_000)
        pool.shutdown()
    }
}
