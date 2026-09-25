package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.wispadmin.controller.ProvisioningV2Controller
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.Instant
import java.util.UUID

class ProvisioningHistoryTest {
    private val ds = DriverManagerDataSource("jdbc:h2:mem:${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")
    private val journal = ProvisioningJournal(JdbcTemplate(ds), jacksonObjectMapper().findAndRegisterModules())
    private val mvc = MockMvcBuilders.standaloneSetup(ProvisioningV2Controller(ProvisioningControlService(journal, "staging"))).build()
    init { ResourceDatabasePopulator(ClassPathResource("db/migration/V56__provisioning_v2_journal.sql")).execute(ds) }

    @Test fun `reopening subscription resolves latest operation without client stored id`() {
        journal.insert(ProvisioningOperation("op", "staging", 42, "HWTC9F4BF950"))
        journal.insert(ProvisioningOperation("foreign", "prod", 42, "ZTEGDC47BFFD"))
        mvc.perform(get("/subscription/42/provisioning"))
            .andExpect(status().isOk).andExpect(jsonPath("$.operation.id").value("op"))
    }

    @Test fun `history retains acknowledged failures and isolates subscription`() {
        val op = journal.insert(ProvisioningOperation("op", "staging", 42, "HWTC9F4BF950"))
        val now = Instant.now()
        val lease = requireNotNull(journal.claim("staging", "op", now, 1000))
        val failed = ProvisioningTransitions().failed(op, ProvisioningStage.VALIDATE,
            ProvisioningFailure("ONU_CONFLICT", "La ONU tiene una asignación previa", false))
        journal.checkpoint(lease, failed, now, true)
        journal.events().forEach { journal.delivered(it.id) }
        mvc.perform(get("/subscription/42/provisioning/history").param("operationId", "op"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[1].operation.checkpoints[0].failure.code").value("ONU_CONFLICT"))
        mvc.perform(get("/subscription/99/provisioning/history").param("operationId", "op"))
            .andExpect(status().isNotFound)
    }
}
