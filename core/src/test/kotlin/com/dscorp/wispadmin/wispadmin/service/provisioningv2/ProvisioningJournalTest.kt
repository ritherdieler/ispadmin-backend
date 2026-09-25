package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.core.io.ClassPathResource
import java.time.Instant
import java.util.UUID

class ProvisioningJournalTest {
    private val dataSource = DriverManagerDataSource("jdbc:h2:mem:${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")
    private val jdbc = JdbcTemplate(dataSource)
    private val json = jacksonObjectMapper().findAndRegisterModules()
    private val now = Instant.parse("2026-09-22T12:00:00Z")
    init { ResourceDatabasePopulator(ClassPathResource("db/migration/V56__provisioning_v2_journal.sql")).execute(dataSource) }
    private fun journal() = ProvisioningJournal(jdbc, json)
    private fun operation() = ProvisioningOperation("op", "staging", 42, "ZTEGDC47BFFD")

    @Test fun `cancellation becomes due immediately after a deferred wait`() {
        val store = journal()
        store.insert(operation())
        val lease = requireNotNull(store.claim("staging", "op", now, 1000))
        store.defer(lease, now.plusSeconds(300))
        store.checkpoint(lease, operation(), now, true)
        store.requestCancel("staging", "op", 1)
        assertEquals(listOf("op"), store.due("staging", now))
    }

    @Test fun `survives restart and environment cannot read or claim another operation`() {
        journal().insert(operation())
        assertEquals(operation(), journal().get("staging", "op"))
        assertNull(journal().get("prod", "op"))
        assertNull(journal().claim("prod", "op", now, 1000))
        val lease = journal().claim("staging", "op", now, 1000)
        assertNotNull(lease)
        assertNull(journal().claim("staging", "op", now, 1000))
    }

    @Test fun `expired worker cannot overwrite new owner and checkpoint writes outbox atomically`() {
        val store = journal()
        store.insert(operation())
        val old = requireNotNull(store.claim("staging", "op", now, 1000))
        val later = now.plusSeconds(2)
        val current = requireNotNull(store.claim("staging", "op", later, 1000))
        assertThrows(IllegalStateException::class.java) { store.checkpoint(old, operation(), later, true) }
        val running = ProvisioningTransitions().started(current.operation, ProvisioningStage.VALIDATE)
        store.checkpoint(current, running, later, true)
        assertEquals(running.copy(updatedAt = later), store.get("staging", "op"))
        assertEquals(2, store.events().size)
    }

    @Test fun `cancel request survives inflight checkpoint and does not release its lease`() {
        val store = journal()
        store.insert(operation())
        val lease = requireNotNull(store.claim("staging", "op", now, 1000))
        store.requestCancel("staging", "op", 1)
        assertNull(store.claim("staging", "op", now, 1000))
        val running = ProvisioningTransitions().started(lease.operation, ProvisioningStage.VALIDATE)
        store.checkpoint(lease, running, now, true)
        assertEquals(ProvisioningState.CANCEL_REQUESTED, store.get("staging", "op")?.state)
        assertEquals(true, store.get("staging", "op")?.checkpoints?.first()?.touched)
    }
}
