package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import javax.persistence.Entity
import javax.persistence.Id

@Entity
class ProvisioningTransactionProbe(@Id var id: Long = 0)

class ProvisioningJpaTransactionTest {
    @Test fun `journal failure rolls back registration even when caller catches it`() {
        val source = DriverManagerDataSource("jdbc:h2:mem:${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")
        val jdbc = JdbcTemplate(source)
        ResourceDatabasePopulator(ClassPathResource("db/migration/V56__provisioning_v2_journal.sql")).execute(source)
        val factory = LocalContainerEntityManagerFactoryBean().apply {
            dataSource = source
            jpaVendorAdapter = HibernateJpaVendorAdapter()
            setPackagesToScan("com.dscorp.wispadmin.wispadmin.service.provisioningv2")
            setJpaPropertyMap(mapOf("hibernate.hbm2ddl.auto" to "create-drop"))
            afterPropertiesSet()
        }
        try {
            val manager = JpaTransactionManager(requireNotNull(factory.`object`))
            val journal = ProvisioningInfrastructureConfig().provisioningJournal(source,
                jacksonObjectMapper().findAndRegisterModules(), manager)
            val operation = ProvisioningOperation("op", "staging", 42, "ZTEGDC47BFFD")
            journal.insert(operation)
            assertThrows(org.springframework.transaction.UnexpectedRollbackException::class.java) {
                TransactionTemplate(manager).executeWithoutResult {
                    jdbc.update("INSERT INTO ProvisioningTransactionProbe (id) VALUES (42)")
                    try { journal.insert(operation) } catch (_: org.springframework.dao.DuplicateKeyException) { }
                }
            }
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ProvisioningTransactionProbe", Int::class.java))
            assertEquals(1, journal.events().size)
        } finally { factory.destroy() }
    }
}
