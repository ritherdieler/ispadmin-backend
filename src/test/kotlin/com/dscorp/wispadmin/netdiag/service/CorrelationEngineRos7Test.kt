package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.config.NetDiagProperties
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncident
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTarget
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagTargetRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Optional

class CorrelationEngineRos7Test {

    private val incidentRepository = mockk<NetDiagIncidentRepository>()
    private val targetRepository = mockk<NetDiagTargetRepository>()
    private val engine = CorrelationEngine(
        incidentRepository = incidentRepository,
        targetRepository = targetRepository,
        properties = NetDiagProperties()
    )

    @Test
    fun `UPSTREAM_PROBE_FAIL en mismo target suprime LINK_DOWN`() {
        val target = NetDiagTarget(id = 1L, name = "MK1", deviceRefId = 7L)
        val upstream = NetDiagIncident(
            id = 9L,
            target = target,
            dedupKey = "UPSTREAM_PROBE_FAIL:1:upstream-http",
            status = "OPEN",
            severity = "P0",
            title = "Upstream fail",
            reasonCode = "UPSTREAM_PROBE_FAIL"
        )
        every { incidentRepository.findByTarget_IdAndStatus(1L, "OPEN") } returns listOf(upstream)
        every { targetRepository.findById(1L) } returns Optional.of(target)

        val suppressor = engine.findSuppressingAncestorIncident(1L, "LINK_DOWN")

        assertEquals(9L, suppressor?.id)
    }

    @Test
    fun `UPSTREAM_PROBE_FAIL no suprime AUTH_FAILURE`() {
        val target = NetDiagTarget(id = 1L, name = "MK1", deviceRefId = 7L)
        val upstream = NetDiagIncident(
            id = 9L,
            target = target,
            dedupKey = "UPSTREAM_PROBE_FAIL:1:upstream-http",
            status = "OPEN",
            severity = "P0",
            title = "Upstream fail",
            reasonCode = "UPSTREAM_PROBE_FAIL"
        )
        every { incidentRepository.findByTarget_IdAndStatus(1L, "OPEN") } returns listOf(upstream)
        every { targetRepository.findById(1L) } returns Optional.of(target)
        every { incidentRepository.existsByTarget_IdAndStatus(any(), "OPEN") } returns false

        val suppressor = engine.findSuppressingAncestorIncident(1L, "AUTH_FAILURE")

        assertNull(suppressor)
    }
}
