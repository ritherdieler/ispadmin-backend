package com.dscorp.wispadmin.oltgateway.service

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

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

    @Test
    fun `SUMMARY incluye metrica local del CLI bus`() {
        every { signalPollService.pollSignals() } returns SignalPollResult(
            slotsPolled = 2,
            portsPolled = 16,
            portsFailed = 1,
            onusUpdated = 10,
            durationMs = 50,
            localQueueDepth = 2,
            localBusyJobType = "WRITE"
        )

        val messages = captureLogs(OltSignalPollScheduler::class.java.name) {
            scheduler.scheduledSignalPoll()
        }

        val summary = messages.single { it.contains("SNMP_OPTICAL_POLL_SUMMARY") }
        assertTrue(summary.contains("localCliBus=true"))
        assertTrue(summary.contains("localQueueDepth=2"))
        assertTrue(summary.contains("localBusyJobType=WRITE"))
        assertTrue(summary.contains("sshActive=n/a"))
        assertTrue(summary.contains("sshMax=n/a"))
    }

    private fun captureLogs(loggerName: String, block: () -> Unit): List<String> {
        val logger = LoggerFactory.getLogger(loggerName) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        try {
            block()
            return appender.list.map { it.formattedMessage }
        } finally {
            logger.detachAppender(appender)
        }
    }
}
