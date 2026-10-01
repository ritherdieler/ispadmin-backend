package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.time.Duration
import java.time.Instant
import java.util.UUID

class ProvisioningEventRetentionTest {
    private val dataSource = DriverManagerDataSource("jdbc:h2:mem:${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")
    private val jdbc = JdbcTemplate(dataSource)
    private val journal = ProvisioningJournal(jdbc, jacksonObjectMapper().findAndRegisterModules())

    init { ProvisioningTestSchema.initialize(dataSource) }

    private fun eventIds(operationId: String) = jdbc.queryForList(
        "SELECT id FROM provisioning_v2_event WHERE operation_id=? ORDER BY id", Long::class.java, operationId)

    @Test
    fun `purges old delivered events but keeps undelivered, recent and the last event of each operation`() {
        journal.insert(ProvisioningOperation("old", "lab", 1, "ZTEGDC47BFFD"))
        journal.requestCancel("lab", "old", 1)
        journal.insert(ProvisioningOperation("fresh", "lab", 2, "ZTEGDC47BFFE"))
        journal.insert(ProvisioningOperation("pending", "lab", 3, "ZTEGDC47BFFF"))
        journal.requestCancel("lab", "pending", 1)
        val now = Instant.parse("2026-10-01T12:00:00Z")
        val old = now.minus(Duration.ofDays(40)).toEpochMilli()
        jdbc.update("UPDATE provisioning_v2_event SET created_at_epoch_ms=?, delivered=TRUE WHERE operation_id IN ('old','fresh')", old)
        jdbc.update("UPDATE provisioning_v2_event SET created_at_epoch_ms=? WHERE operation_id='fresh'", now.toEpochMilli())
        jdbc.update("UPDATE provisioning_v2_event SET created_at_epoch_ms=?, delivered=FALSE WHERE operation_id='pending'", old)
        val lastOld = eventIds("old").last()

        val purged = journal.purgeDeliveredEvents(now.minus(Duration.ofDays(30)))

        assertEquals(1, purged)
        assertEquals(listOf(lastOld), eventIds("old"))
        assertEquals(1, eventIds("fresh").size)
        assertEquals(2, eventIds("pending").size)
        assertNotNull(journal.latest("lab", 1))
    }
}
