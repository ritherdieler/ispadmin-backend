package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncident
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncidentEvent
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTarget
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentEventRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentRepository
import com.dscorp.wispadmin.netdiag.exception.IncidentNotFoundException
import com.dscorp.wispadmin.netdiag.exception.NetDiagConflictException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.Optional

class NetDiagIncidentQueryServiceTest {

    private val incidentRepository = mockk<NetDiagIncidentRepository>()
    private val incidentEventRepository = mockk<NetDiagIncidentEventRepository>()
    private val maintenanceService = mockk<NetDiagMaintenanceService>(relaxed = true)
    private val service = NetDiagIncidentQueryService(
        incidentRepository,
        incidentEventRepository,
        maintenanceService
    )

    private val target = NetDiagTarget(id = 7L, name = "MK1", deviceRefId = 7L)

    @Test
    fun `lista filtra por severity status targetId y rango de fechas`() {
        val matching = incident(
            id = 1L,
            severity = "P0",
            status = "OPEN",
            openedAt = Instant.parse("2026-07-26T12:00:00Z")
        )
        val other = incident(
            id = 2L,
            severity = "P1",
            status = "OPEN",
            openedAt = Instant.parse("2026-07-26T12:00:00Z")
        )
        every { incidentRepository.findAll() } returns listOf(matching, other)
        every { incidentEventRepository.findByIncidentIdOrderByCreatedAtDesc(any()) } returns emptyList()

        val result = service.listIncidents(
            severity = "P0",
            status = "OPEN",
            targetId = 7L,
            dateFrom = "2026-07-26T00:00:00Z",
            dateTo = "2026-07-27T00:00:00Z"
        )

        assertEquals(1, result.size)
        assertEquals(1L, result[0].id)
        assertEquals("MK1", result[0].targetName)
    }

    @Test
    fun `ack marca ACKNOWLEDGED y agrega evento`() {
        val open = incident(id = 9L, status = "OPEN")
        every { incidentRepository.findById(9L) } returns Optional.of(open)
        every { incidentRepository.save(any()) } answers { firstArg() }
        every { incidentEventRepository.save(any()) } answers { firstArg<NetDiagIncidentEvent>().also { it.id = 1L } }
        every { incidentEventRepository.findByIncidentIdOrderByCreatedAtDesc(9L) } returns emptyList()

        val detail = service.acknowledge(9L)

        assertEquals("ACKNOWLEDGED", detail.status)
        assertNotNull(detail.acknowledgedAt)
        verify { incidentEventRepository.save(match { it.type == "ACKNOWLEDGED" }) }
    }

    @Test
    fun `resolve marca RESOLVED`() {
        val open = incident(id = 9L, status = "OPEN")
        every { incidentRepository.findById(9L) } returns Optional.of(open)
        every { incidentRepository.save(any()) } answers { firstArg() }
        every { incidentEventRepository.save(any()) } answers { firstArg<NetDiagIncidentEvent>().also { it.id = 1L } }
        every { incidentEventRepository.findByIncidentIdOrderByCreatedAtDesc(9L) } returns emptyList()

        val detail = service.resolve(9L)

        assertEquals("RESOLVED", detail.status)
        assertNotNull(detail.resolvedAt)
        verify { incidentEventRepository.save(match { it.type == "RESOLVED" }) }
    }

    @Test
    fun `no permite resolve de incidente ya resuelto`() {
        val resolved = incident(id = 9L, status = "RESOLVED")
        every { incidentRepository.findById(9L) } returns Optional.of(resolved)

        assertThrows<NetDiagConflictException> { service.resolve(9L) }
    }

    @Test
    fun `silence marca SILENCED y persiste silencedUntil`() {
        val open = incident(id = 9L, status = "OPEN")
        every { incidentRepository.findById(9L) } returns Optional.of(open)
        every { incidentRepository.save(any()) } answers { firstArg() }
        every { incidentEventRepository.save(any()) } answers { firstArg<NetDiagIncidentEvent>().also { it.id = 1L } }
        every { incidentEventRepository.findByIncidentIdOrderByCreatedAtDesc(9L) } returns emptyList()
        val until = Instant.parse("2026-07-26T18:00:00Z")

        val detail = service.silence(9L, until)

        assertEquals("SILENCED", detail.status)
        assertEquals(until, detail.silencedUntil)
        verify { incidentEventRepository.save(match { it.type == "SILENCED" }) }
    }

    @Test
    fun `ack de inexistente lanza not found`() {
        every { incidentRepository.findById(99L) } returns Optional.empty()
        assertThrows<IncidentNotFoundException> { service.acknowledge(99L) }
    }

    private fun incident(
        id: Long,
        severity: String = "P0",
        status: String = "OPEN",
        openedAt: Instant = Instant.parse("2026-07-26T12:00:00Z")
    ): NetDiagIncident {
        return NetDiagIncident(
            id = id,
            target = target,
            dedupKey = "LINK_DOWN:7:ether1",
            status = status,
            severity = severity,
            title = "Link down",
            reasonCode = "LINK_DOWN",
            openedAt = openedAt
        )
    }
}
