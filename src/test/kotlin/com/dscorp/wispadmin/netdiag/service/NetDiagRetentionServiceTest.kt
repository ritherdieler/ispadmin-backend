package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.config.NetDiagProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class NetDiagRetentionServiceTest {

    private val writer = mockk<NetDiagRetentionWriter>(relaxed = true)
    private val properties = NetDiagProperties().apply {
        retention.oltLogEventDays = 14
        retention.incidentEventDays = 90
        retention.alertDecisionDays = 7
        retention.notificationLogDays = 90
        retention.suppressionDays = 30
        retention.batchSize = 100
        retention.maxBatchesPerRun = 10
    }
    private val service = NetDiagRetentionService(properties, writer)
    private val now = Instant.parse("2026-09-01T03:45:00Z")

    @Test
    fun `borra por lotes hasta agotar las filas vencidas`() {
        every { writer.purgeOltLogEvents(any(), 100) } returnsMany listOf(100, 100, 40)

        val result = service.purgeExpired(now)

        assertEquals(240, result.oltLogEvents)
        verify(exactly = 3) { writer.purgeOltLogEvents(any(), 100) }
    }

    @Test
    fun `respeta el tope de lotes por ejecucion para no bloquear la base`() {
        every { writer.purgeOltLogEvents(any(), 100) } returns 100

        val result = service.purgeExpired(now)

        assertEquals(1_000, result.oltLogEvents)
        verify(exactly = 10) { writer.purgeOltLogEvents(any(), 100) }
    }

    @Test
    fun `usa el corte de dias configurado por tabla`() {
        val oltCutoff = slot<Instant>()
        val incidentCutoff = slot<Instant>()
        every { writer.purgeOltLogEvents(capture(oltCutoff), any()) } returns 0
        every { writer.purgeIncidentEvents(capture(incidentCutoff), any()) } returns 0

        service.purgeExpired(now)

        assertEquals(now.minus(14, ChronoUnit.DAYS), oltCutoff.captured)
        assertEquals(now.minus(90, ChronoUnit.DAYS), incidentCutoff.captured)
    }

    @Test
    fun `no purga una tabla con retencion desactivada`() {
        properties.retention.alertDecisionDays = 0

        service.purgeExpired(now)

        verify(exactly = 0) { writer.purgeAlertDecisions(any(), any()) }
    }

    @Test
    fun `agrega el total borrado de todas las tablas`() {
        every { writer.purgeOltLogEvents(any(), any()) } returnsMany listOf(10, 0)
        every { writer.purgeIncidentEvents(any(), any()) } returnsMany listOf(5, 0)
        every { writer.purgeAlertDecisions(any(), any()) } returnsMany listOf(3, 0)
        every { writer.purgeNotificationLogs(any(), any()) } returnsMany listOf(2, 0)
        every { writer.purgeSuppressionWindows(any(), any()) } returnsMany listOf(1, 0)

        val result = service.purgeExpired(now)

        assertEquals(10, result.oltLogEvents)
        assertEquals(5, result.incidentEvents)
        assertEquals(3, result.alertDecisions)
        assertEquals(2, result.notificationLogs)
        assertEquals(1, result.suppressionWindows)
        assertEquals(21, result.total())
    }

    @Test
    fun `un fallo en una tabla no impide purgar el resto`() {
        every { writer.purgeOltLogEvents(any(), any()) } throws IllegalStateException("lock wait timeout")
        every { writer.purgeIncidentEvents(any(), any()) } returnsMany listOf(4, 0)

        val result = service.purgeExpired(now)

        assertEquals(0, result.oltLogEvents)
        assertEquals(4, result.incidentEvents)
        assertTrue(result.total() > 0)
    }
}
