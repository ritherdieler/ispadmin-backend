package com.dscorp.wispadmin.acs.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

class AcsPersistenceConfigTest {

    @Test
    fun repositoriesBindToAcsPersistenceUnit() {
        val jpa = AcsPersistenceConfig::class.java.getAnnotation(EnableJpaRepositories::class.java)
        assertTrue(jpa.basePackages.contains("com.dscorp.wispadmin.acs"))
        assertEquals("acsEntityManagerFactory", jpa.entityManagerFactoryRef)
        assertEquals("acsTransactionManager", jpa.transactionManagerRef)
    }
}
