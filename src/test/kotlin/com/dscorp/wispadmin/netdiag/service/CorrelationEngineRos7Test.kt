package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.config.NetDiagProperties
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncident
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTarget
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagTargetRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
        every {
            incidentRepository.findByTarget_IdAndStatusAndReasonCode(1L, "OPEN", "UPSTREAM_PROBE_FAIL")
        } returns listOf(upstream)
        every { targetRepository.findById(1L) } returns Optional.of(target)

        val suppressor = engine.findSuppressingAncestorIncident(1L, "LINK_DOWN")

        assertEquals(9L, suppressor?.id)
    }

    @Test
    fun `incidente OPEN del padre suprime con una sola consulta`() {
        val parent = NetDiagTarget(id = 1L, name = "OLT", deviceRefId = 10L)
        val child = NetDiagTarget(id = 2L, name = "PON", deviceRefId = 20L, parentTargetId = 1L)
        val parentIncident = NetDiagIncident(
            id = 10L,
            target = parent,
            dedupKey = "DEVICE_UNREACHABLE:1:poll",
            status = "OPEN",
            severity = "P0",
            title = "OLT down",
            reasonCode = "DEVICE_UNREACHABLE"
        )
        every {
            incidentRepository.findByTarget_IdAndStatusAndReasonCode(any(), "OPEN", "UPSTREAM_PROBE_FAIL")
        } returns emptyList()
        every { targetRepository.findById(2L) } returns Optional.of(child)
        every { incidentRepository.findByTarget_IdAndStatus(1L, "OPEN") } returns listOf(parentIncident)

        val suppressor = engine.findSuppressingAncestorIncident(2L, "PON_DOWN")

        assertEquals(10L, suppressor?.id)
        verify(exactly = 1) { incidentRepository.findByTarget_IdAndStatus(1L, "OPEN") }
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
        every {
            incidentRepository.findByTarget_IdAndStatusAndReasonCode(1L, "OPEN", "UPSTREAM_PROBE_FAIL")
        } returns listOf(upstream)
        every { targetRepository.findById(1L) } returns Optional.of(target)

        val suppressor = engine.findSuppressingAncestorIncident(1L, "AUTH_FAILURE")

        assertNull(suppressor)
    }
}
