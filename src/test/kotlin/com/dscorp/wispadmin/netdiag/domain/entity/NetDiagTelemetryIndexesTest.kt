package com.dscorp.wispadmin.netdiag.domain.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.persistence.Table

class NetDiagTelemetryIndexesTest {

    private fun indexedColumnsOf(entity: Class<*>): List<String> =
        entity.getAnnotation(Table::class.java).indexes.map { it.columnList.replace(" ", "") }

    @Test
    fun `net_diag_olt_log_event indexa las columnas de filtro y orden del endpoint de logs`() {
        val columns = indexedColumnsOf(NetDiagOltLogEvent::class.java)

        assertTrue(columns.contains("received_at"), columns.toString())
        assertTrue(columns.contains("board,port,received_at"), columns.toString())
        assertTrue(columns.contains("is_unparsed,received_at"), columns.toString())
        assertTrue(columns.contains("target_id,received_at"), columns.toString())
    }

    @Test
    fun `las tablas con retencion indexan su columna de fecha`() {
        assertTrue(indexedColumnsOf(NetDiagIncidentEvent::class.java).contains("created_at"))
        assertTrue(indexedColumnsOf(NetDiagAlertDecision::class.java).contains("created_at"))
        assertTrue(indexedColumnsOf(NetDiagNotificationLog::class.java).contains("created_at"))
        assertTrue(indexedColumnsOf(NetDiagAlertSuppressionWindow::class.java).contains("window_start"))
    }

    @Test
    fun `el historial de incidente conserva el codigo de alerta y el target de la decision fusionada`() {
        val event = NetDiagIncidentEvent(
            incident = NetDiagIncident(
                dedupKey = "LINK_DOWN:1:ether1",
                status = "OPEN",
                severity = "P0",
                title = "Link down"
            ),
            type = "OPENED",
            reasonCode = "LINK_DOWN",
            targetId = 7L
        )

        assertEquals("LINK_DOWN", event.reasonCode)
        assertEquals(7L, event.targetId)
    }
}
