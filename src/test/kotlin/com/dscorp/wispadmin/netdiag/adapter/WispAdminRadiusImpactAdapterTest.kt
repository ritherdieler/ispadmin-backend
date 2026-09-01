package com.dscorp.wispadmin.netdiag.adapter

import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagProbeRun
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTarget
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagProbeRunRepository
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional

class WispAdminRadiusImpactAdapterTest {

    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val probeRunRepository = mockk<NetDiagProbeRunRepository>()
    private val adapter = WispAdminRadiusImpactAdapter(
        subscriptionRepository = subscriptionRepository,
        probeRunRepository = probeRunRepository,
        objectMapper = ObjectMapper()
    )

    @Test
    fun `usa pppActiveCount del ultimo probe cuando existe`() {
        every { subscriptionRepository.countByServiceStatus(ServiceStatus.ACTIVE) } returns 1500L
        val target = NetDiagTarget(id = 3L, name = "MK1", deviceRefId = 7L)
        every { probeRunRepository.findTopByTargetIdOrderByStartedAtDesc(3L) } returns Optional.of(
            NetDiagProbeRun(
                id = 1L,
                target = target,
                status = "SUCCESS",
                startedAt = Instant.parse("2026-07-26T12:00:00Z"),
                payload = """{"pppActiveCount":88}"""
            )
        )

        val impact = adapter.estimateImpact(3L, 7L)

        assertEquals(1500L, impact.activeSubscriptions)
        assertEquals(88, impact.pppActiveSessions)
        assertEquals(88L, impact.estimatedAffected)
    }

    @Test
    fun `sin probe usa total de suscripciones activas`() {
        every { subscriptionRepository.countByServiceStatus(ServiceStatus.ACTIVE) } returns 900L
        every { probeRunRepository.findTopByTargetIdOrderByStartedAtDesc(3L) } returns Optional.empty()

        val impact = adapter.estimateImpact(3L, 7L)

        assertEquals(900L, impact.activeSubscriptions)
        assertEquals(null, impact.pppActiveSessions)
        assertEquals(null, impact.estimatedAffected)
    }
}
