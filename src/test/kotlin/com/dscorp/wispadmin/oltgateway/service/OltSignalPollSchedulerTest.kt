package com.dscorp.wispadmin.oltgateway.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class OltSignalPollSchedulerTest {

    private val signalPollService = mockk<OltSignalPollService>()
    private val scheduler = OltSignalPollScheduler(signalPollService)

    @Test
    fun `scheduledSignalPoll delega a pollSignals`() {
        every { signalPollService.pollSignals() } returns SignalPollResult(
            slotsPolled = 2,
            portsPolled = 32,
            onusUpdated = 10,
            durationMs = 50
        )

        scheduler.scheduledSignalPoll()

        verify(exactly = 1) { signalPollService.pollSignals() }
    }

    @Test
    fun `scheduledSignalPoll no falla cuando skip`() {
        every { signalPollService.pollSignals() } returns SignalPollResult(skippedReason = "already_queued")

        scheduler.scheduledSignalPoll()

        verify(exactly = 1) { signalPollService.pollSignals() }
    }
}
