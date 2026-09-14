package com.dscorp.wispadmin.netdiag.adapter

import com.dscorp.wispadmin.netdiag.config.NetDiagProperties
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncident
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagOltLogEvent
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTarget
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentEventRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagMaintenanceWindowRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagOltLogEventRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagProbeRunRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagTargetRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import java.time.Instant
import java.util.Optional

class HealthNetDiagAdapterTest {

    private val targets = mockk<NetDiagTargetRepository>()
    private val probes = mockk<NetDiagProbeRunRepository>()
    private val incidents = mockk<NetDiagIncidentRepository>()
    private val incidentEvents = mockk<NetDiagIncidentEventRepository>(relaxed = true)
    private val maintenance = mockk<NetDiagMaintenanceWindowRepository>()
    private val logs = mockk<NetDiagOltLogEventRepository>()
    private val adapter = HealthNetDiagAdapter(
        targets, probes, incidents, incidentEvents, maintenance, logs, NetDiagProperties()
    )

    @Test
    fun `enabled targets map device and parent`() {
        every { targets.findByEnabledTrue() } returns listOf(
            NetDiagTarget(id = 10, name = "PON-a", deviceRefId = 3, parentTargetId = 1, pollIntervalMs = 15000)
        )

        val dto = adapter.enabledTargets().single()

        assertEquals(10L, dto.id)
        assertEquals(3L, dto.deviceRefId)
        assertEquals(1L, dto.parentTargetId)
        assertEquals(85, adapter.cpuThreshold())
    }

    @Test
    fun `olt log changes drop rows without id`() {
        val at = Instant.parse("2026-08-31T20:00:00Z")
        every { logs.findChanges(at, 0L, any()) } returns listOf(
            NetDiagOltLogEvent(id = null, receivedAt = at, reasonCode = "ONT_OFFLINE"),
            NetDiagOltLogEvent(id = 4L, receivedAt = at, reasonCode = "ONT_OFFLINE", board = 0, port = 1, onuIndex = 8, targetId = 10)
        )

        val result = adapter.findOltLogChanges(at, null, PageRequest.of(0, 10))

        assertEquals(1, result.size)
        assertEquals(4L, result.single().id)
        assertFalse(result.single().isUnparsed)
    }

    @Test
    fun `save incident uses the live target entity`() {
        val target = NetDiagTarget(id = 10, name = "PON-a", deviceRefId = 3)
        every { targets.findById(10) } returns Optional.of(target)
        every { incidents.save(any()) } answers {
            firstArg<NetDiagIncident>().also { it.id = 77L }
        }

        val saved = adapter.saveIncident(
            com.dscorp.wispadmin.servicehealth.port.HealthNetDiagTarget(10, "PON-a", 3, null, 60000, null),
            "service-health:1:0:1",
            "OPEN",
            "P1",
            "Caida",
            "GPON_SHARED_DOWN",
            Instant.parse("2026-08-31T20:00:00Z")
        )

        assertEquals(77L, saved.id)
        assertEquals("GPON_SHARED_DOWN", saved.reasonCode)
        assertEquals(10L, saved.targetId)
    }
}
