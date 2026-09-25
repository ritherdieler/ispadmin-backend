package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmSecretCipher
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import java.time.Instant
import java.util.UUID

class ProvisioningResourceStoreTest {
    private val dataSource = DriverManagerDataSource("jdbc:h2:mem:${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")
    private val jdbc = JdbcTemplate(dataSource)
    private val journal = ProvisioningJournal(jdbc, jacksonObjectMapper().findAndRegisterModules())
    private val resources = ProvisioningResourceStore(jdbc, CrmSecretCipher("unit-test-only"))
    private val now = Instant.parse("2026-09-22T12:00:00Z")
    private val lease: ProvisioningLease
    init {
        ResourceDatabasePopulator(ClassPathResource("db/migration/V56__provisioning_v2_journal.sql")).execute(dataSource)
        journal.insert(ProvisioningOperation("op", "staging", 42, "ZTEGDC47BFFD"))
        lease = requireNotNull(journal.claim("staging", "op", now, 1000))
    }

    @Test fun `baseline is encrypted immutable and isolated from another environment`() {
        resources.capture(lease, "wifi", "original-password", now)
        assertEquals("original-password", resources.snapshot("staging", "op", "wifi"))
        assertNull(resources.snapshot("prod", "op", "wifi"))
        val stored = jdbc.queryForObject("SELECT snapshot_cipher FROM provisioning_v2_resource", String::class.java)
        assertFalse(requireNotNull(stored).contains("original-password"))
        resources.capture(lease, "wifi", "original-password", now)
        assertThrows(IllegalStateException::class.java) { resources.capture(lease, "wifi", "changed-password", now) }
        assertEquals("original-password", resources.snapshot("staging", "op", "wifi"))
    }

    @Test fun `expired owner cannot register a resource`() {
        assertThrows(IllegalStateException::class.java) { resources.capture(lease, "wifi", "secret", now.plusSeconds(2)) }
        assertNull(resources.snapshot("staging", "op", "wifi"))
    }

    @Test fun `initial registration snapshot is encrypted immutable before a worker claims operation`() {
        resources.captureInitial(ProvisioningOperation("op", "staging", 42, "ZTEGDC47BFFD"), "registration", "wifi-secret")

        assertEquals("wifi-secret", resources.snapshot("staging", "op", "registration"))
        assertThrows(IllegalStateException::class.java) {
            resources.captureInitial(ProvisioningOperation("op", "staging", 42, "ZTEGDC47BFFD"), "registration", "changed")
        }
    }
}
