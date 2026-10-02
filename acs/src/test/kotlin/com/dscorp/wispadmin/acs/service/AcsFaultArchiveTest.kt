package com.dscorp.wispadmin.acs.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.time.Instant

class AcsFaultArchiveTest {
    @Test
    fun `repeated snapshots deduplicate faults and a successful empty snapshot resolves them`() {
        val source = DriverManagerDataSource("jdbc:h2:mem:acs_fault_archive;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")
        val jdbc = JdbcTemplate(source)
        jdbc.execute("""CREATE TABLE acs_fault_history (
            device_id VARCHAR(256) NOT NULL, fault_id VARCHAR(256) NOT NULL, sn VARCHAR(64) NOT NULL,
            channel VARCHAR(256), task_id VARCHAR(256), code VARCHAR(64) NOT NULL,
            description VARCHAR(256) NOT NULL, parameters_json TEXT NOT NULL, retries INT,
            occurred_at VARCHAR(40), first_seen_at VARCHAR(40) NOT NULL,
            last_seen_at VARCHAR(40) NOT NULL, resolved_at VARCHAR(40),
            PRIMARY KEY (device_id, fault_id))""")
        val archive = AcsFaultArchive(jdbc)
        val at = Instant.parse("2026-10-02T07:00:00Z")
        val fault = CpeFaultView(
            id = "device-1:task_42", deviceId = "device-1", channel = "task_42", taskId = "42",
            occurredAt = at.toString(), retries = 1, code = "cwmp.9003",
            description = "Parámetros rechazados por el equipo", parameters = listOf("InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.SSID"),
        )

        archive.capture("device-1", "ZTEGDC47BFFD", listOf(fault), at)
        archive.capture("device-1", "ZTEGDC47BFFD", listOf(fault.copy(retries = 2)), at.plusSeconds(60))
        val current = archive.page("device-1", 0, 20)
        assertEquals(1, current.total)
        assertEquals(2, current.items.single().retries)
        assertNull(current.items.single().resolvedAt)
        assertEquals("42", current.items.single().taskId)

        archive.capture("device-1", "ZTEGDC47BFFD", emptyList(), at.plusSeconds(120))
        val resolved = archive.page("device-1", 0, 20).items.single()
        assertEquals("2026-10-02T07:02:00Z", resolved.resolvedAt)
        assertEquals("2026-10-02T07:00:00Z", resolved.firstSeenAt)
        assertEquals("2026-10-02T07:01:00Z", resolved.lastSeenAt)

        archive.capture("device-1", "ZTEGDC47BFFD", listOf(fault.copy(id = "device-1:inform", channel = "inform", taskId = null)), at.plusSeconds(180))
        assertEquals(2, archive.page("device-1", 0, 1).total)
        assertEquals("device-1:inform", archive.page("device-1", 0, 1).items.single().id)
        assertEquals("device-1:task_42", archive.page("device-1", 1, 1).items.single().id)
    }
}
