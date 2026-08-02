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
        every {
            incidentRepository.findForList(
                listOf("OPEN"),
                "P0",
                7L,
                Instant.parse("2026-07-26T00:00:00Z"),
                Instant.parse("2026-07-27T00:00:00Z")
            )
        } returns listOf(matching)

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
        verify(exactly = 0) { incidentRepository.findAll() }
    }

    @Test
    fun `lista sin status usa estados activos por defecto`() {
        val open = incident(id = 1L, status = "OPEN")
        every {
            incidentRepository.findForList(
                listOf("OPEN", "ACKNOWLEDGED", "SILENCED"),
                null,
                null,
                null,
                null
            )
        } returns listOf(open)

        val result = service.listIncidents()

        assertEquals(listOf(1L), result.map { it.id })
        verify(exactly = 0) { incidentRepository.findAll() }
    }

    @Test
    fun `lista con status RESOLVED lo respeta tal cual`() {
        val resolved = incident(id = 3L, status = "RESOLVED")
        every {
            incidentRepository.findForList(listOf("RESOLVED"), null, null, null, null)
        } returns listOf(resolved)

        val result = service.listIncidents(status = "resolved")

        assertEquals("RESOLVED", result[0].status)
    }

    @Test
    fun `lista parsea fechas LocalDate a limites del dia`() {
        every {
            incidentRepository.findForList(
                listOf("OPEN", "ACKNOWLEDGED", "SILENCED"),
                null,
                null,
                Instant.parse("2026-07-26T00:00:00Z"),
                Instant.parse("2026-07-26T23:59:59.999Z")
            )
        } returns emptyList()

        val result = service.listIncidents(dateFrom = "2026-07-26", dateTo = "2026-07-26")

        assertEquals(0, result.size)
    }

    @Test
    fun `detalle carga incidente con target y expone targetName`() {
        val open = incident(id = 5L, status = "OPEN")
        every { incidentRepository.findByIdWithTarget(5L) } returns Optional.of(open)
        every { incidentEventRepository.findByIncidentIdOrderByCreatedAtDesc(5L) } returns emptyList()

        val detail = service.getIncident(5L)

        assertEquals("MK1", detail.targetName)
        assertEquals(7L, detail.targetId)
        verify(exactly = 0) { incidentRepository.findById(any<Long>()) }
    }

    @Test
    fun `summarize cuenta abiertos p0 y poll stale con agregaciones`() {
        every { incidentRepository.countByStatusIn(listOf("OPEN", "ACKNOWLEDGED")) } returns 12L
        every {
            incidentRepository.countBySeverityAndStatusIn("P0", listOf("OPEN", "ACKNOWLEDGED"))
        } returns 3L
        every {
            incidentRepository.countByReasonCodeAndStatusIn("POLL_STALE", listOf("OPEN", "ACKNOWLEDGED"))
        } returns 2L

        val summary = service.summarizeIncidents()

        assertEquals(12L, summary.openCount)
        assertEquals(3L, summary.p0OpenCount)
        assertEquals(2L, summary.pollStaleCount)
        verify(exactly = 0) { incidentRepository.findAll() }
    }

    @Test
    fun `ack marca ACKNOWLEDGED y agrega evento`() {
        val open = incident(id = 9L, status = "OPEN")
        every { incidentRepository.findByIdWithTarget(9L) } returns Optional.of(open)
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
        every { incidentRepository.findByIdWithTarget(9L) } returns Optional.of(open)
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
        every { incidentRepository.findByIdWithTarget(9L) } returns Optional.of(resolved)

        assertThrows<NetDiagConflictException> { service.resolve(9L) }
    }

    @Test
    fun `silence marca SILENCED y persiste silencedUntil`() {
        val open = incident(id = 9L, status = "OPEN")
        every { incidentRepository.findByIdWithTarget(9L) } returns Optional.of(open)
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
        every { incidentRepository.findByIdWithTarget(99L) } returns Optional.empty()
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
