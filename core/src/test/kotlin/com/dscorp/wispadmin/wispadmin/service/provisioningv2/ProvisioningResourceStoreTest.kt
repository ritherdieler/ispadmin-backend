package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmSecretCipher
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
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
        ProvisioningTestSchema.initialize(dataSource)
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

    @Test fun `preauthorization draft is encrypted and can be replaced while restoring the same operation`() {
        val operation = ProvisioningOperation(
            id = "preauth",
            environment = "staging",
            subscriptionId = null,
            serial = "VSOL0031C0B6",
            flowVersion = 3,
            phase = ProvisioningPhase.OLT_AUTHORIZATION,
            state = ProvisioningState.PENDING,
            operatorId = 71,
            registrationRequestKey = "draft-request-1",
            onuTarget = ProvisioningOnuTarget("olt", "GPON", "0", "1", "VSOLVA74", 100),
        )
        journal.insertPreauthorization(operation)
        journal.updatePreauthorization("staging", "preauth", operation.revision, transform = { current ->
            current.copy(phase = ProvisioningPhase.READY_FOR_FORM, state = ProvisioningState.READY_FOR_FORM)
        })

        resources.savePreauthorizationResource("staging", "preauth", 71, "registration-draft", "{\"dni\":\"private-value\"}")
        assertEquals("{\"dni\":\"private-value\"}", resources.preauthorizationResource("staging", "preauth", 71, "registration-draft"))
        val encrypted = jdbc.queryForObject("SELECT snapshot_cipher FROM provisioning_v2_resource WHERE operation_id='preauth'", String::class.java)
        assertFalse(requireNotNull(encrypted).contains("private-value"))

        resources.savePreauthorizationResource("staging", "preauth", 71, "registration-draft", "{\"dni\":\"updated-value\"}")
        assertEquals("{\"dni\":\"updated-value\"}", resources.preauthorizationResource("staging", "preauth", 71, "registration-draft"))
    }
}
