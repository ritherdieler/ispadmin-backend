package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.config.NetDiagProperties
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagProbeRun
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTarget
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagProbeRunRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagTargetRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional

class NetDiagPollServiceTest {

    private val targetRepository = mockk<NetDiagTargetRepository>()
    private val probeRunRepository = mockk<NetDiagProbeRunRepository>()
    private val pollAdapter = mockk<MikrotikPollAdapter>()
    private val signalExtractor = mockk<AlertSignalExtractor>()
    private val alertEvaluator = mockk<AlertEvaluator>(relaxed = true)
    private val properties = NetDiagProperties().apply {
        poll.concurrency = 2
        poll.jitterMs = 0
        poll.intervalMs = 60000
        alert.staleMultiplier = 3
        retention.probeRunDays = 30
    }
    private val service = NetDiagPollService(
        targetRepository = targetRepository,
        probeRunRepository = probeRunRepository,
        pollAdapter = pollAdapter,
        signalExtractor = signalExtractor,
        alertEvaluator = alertEvaluator,
        properties = properties
    )

    @Test
    fun `pollAllEnabledTargets evalua señales del snapshot`() {
        val target = NetDiagTarget(id = 1L, name = "MK1", deviceRefId = 7L)
        every { targetRepository.findByEnabledTrue() } returns listOf(target)
        val snapshot = PollSnapshot(
            interfaces = emptyList(),
            health = emptyList(),
            routerboard = null,
            resource = null,
            criticalInterfaces = emptyList(),
            expectedFirmware = null,
            previousUptimeSeconds = null
        )
        val probe = NetDiagProbeRun(id = 1L, target = target, status = "SUCCESS")
        every { pollAdapter.poll(target) } returns PollResult(
            probeRun = probe,
            status = "SUCCESS",
            payload = "{}",
            snapshot = snapshot
        )
        every { signalExtractor.fromSnapshot(1L, snapshot) } returns emptyList()
        every { probeRunRepository.findTopByTargetIdOrderByStartedAtDesc(1L) } returns Optional.of(probe)

        service.pollAllEnabledTargets()

        verify { pollAdapter.poll(target) }
        verify { alertEvaluator.evaluate(1L, emptyList()) }
    }

    @Test
    fun `detecta POLL_STALE cuando no hay probe reciente`() {
        val target = NetDiagTarget(id = 1L, name = "MK1", deviceRefId = 7L, pollIntervalMs = 60000)
        every { targetRepository.findByEnabledTrue() } returns listOf(target)
        every { pollAdapter.poll(target) } returns PollResult(
            probeRun = NetDiagProbeRun(id = 1L, target = target, status = "SUCCESS"),
            status = "SUCCESS",
            payload = "{}",
            snapshot = PollSnapshot(
                interfaces = emptyList(),
                health = emptyList(),
                routerboard = null,
                resource = null,
                criticalInterfaces = emptyList(),
                expectedFirmware = null,
                previousUptimeSeconds = null
            )
        )
        every { signalExtractor.fromSnapshot(1L, any()) } returns emptyList()
        every { probeRunRepository.findTopByTargetIdOrderByStartedAtDesc(1L) } returns Optional.of(
            NetDiagProbeRun(
                id = 2L,
                target = target,
                status = "SUCCESS",
                startedAt = Instant.now().minus(10, ChronoUnit.MINUTES)
            )
        )
        val stale = AlertSignal("POLL_STALE", "P1", "stale", "POLL_STALE:1:poll")
        every { signalExtractor.pollStale(1L, any()) } returns stale

        service.pollAllEnabledTargets()

        verify { alertEvaluator.evaluate(1L, listOf(stale)) }
    }

    @Test
    fun `purgeOldProbeRuns delega en repositorio`() {
        every { probeRunRepository.deleteByStartedAtBefore(any()) } returns 12

        assertEquals(12, service.purgeOldProbeRuns())
    }
}
