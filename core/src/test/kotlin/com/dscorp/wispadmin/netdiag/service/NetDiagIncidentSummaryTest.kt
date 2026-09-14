package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.config.NetDiagProperties
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentEventRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NetDiagIncidentSummaryTest {

    private val incidentRepository = mockk<NetDiagIncidentRepository>()
    private val incidentEventRepository = mockk<NetDiagIncidentEventRepository>(relaxed = true)
    private val maintenanceService = mockk<NetDiagMaintenanceService>(relaxed = true)
    private val properties = NetDiagProperties().apply { alert.summaryCacheMs = 15_000 }
    private val summaryCache = NetDiagIncidentSummaryCache(properties)
    private var clock = 0L

    private val service = NetDiagIncidentQueryService(
        incidentRepository = incidentRepository,
        incidentEventRepository = incidentEventRepository,
        maintenanceService = maintenanceService,
        summaryCache = summaryCache
    )

    @BeforeEach
    fun setup() {
        summaryCache.nowMillis = { clock }
        summaryCache.invalidate()
        every { incidentRepository.summarizeByStatuses(any()) } returns listOf(
            arrayOf<Any?>("P0", "DEVICE_UNREACHABLE", 2L),
            arrayOf<Any?>("P1", "POLL_STALE", 3L),
            arrayOf<Any?>("P0", "POLL_STALE", 1L)
        )
    }

    @Test
    fun `resume incidentes con una sola consulta agrupada`() {
        val summary = service.summarizeIncidents()

        assertEquals(6, summary.openCount)
        assertEquals(3, summary.p0OpenCount)
        assertEquals(4, summary.pollStaleCount)
        verify(exactly = 1) { incidentRepository.summarizeByStatuses(any()) }
        verify(exactly = 0) { incidentRepository.countByStatusIn(any()) }
    }

    @Test
    fun `sirve del cache dentro de la ventana ttl`() {
        service.summarizeIncidents()
        clock = 14_000
        service.summarizeIncidents()

        verify(exactly = 1) { incidentRepository.summarizeByStatuses(any()) }
    }

    @Test
    fun `recalcula al vencer el ttl`() {
        service.summarizeIncidents()
        clock = 16_000
        service.summarizeIncidents()

        verify(exactly = 2) { incidentRepository.summarizeByStatuses(any()) }
    }

    @Test
    fun `invalidar fuerza el recalculo inmediato`() {
        service.summarizeIncidents()
        summaryCache.invalidate()
        service.summarizeIncidents()

        verify(exactly = 2) { incidentRepository.summarizeByStatuses(any()) }
    }

    @Test
    fun `tolera filas con severidad o codigo nulos`() {
        every { incidentRepository.summarizeByStatuses(any()) } returns listOf(
            arrayOf<Any?>("P0", null, 2L),
            arrayOf<Any?>(null, "POLL_STALE", 5L)
        )

        val summary = service.summarizeIncidents()

        assertEquals(7, summary.openCount)
        assertEquals(2, summary.p0OpenCount)
        assertEquals(5, summary.pollStaleCount)
    }
}
