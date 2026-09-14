package com.dscorp.wispadmin.wispadmin.config

import com.dscorp.wispadmin.wispadmin.TransactionConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class CoreJpaConfigTest {

    @Test
    fun entityManagerFactoryIsPrimaryAndNamedForEnableJpaRepositories() {
        val method = CoreJpaConfig::class.java.declaredMethods.first { it.name == "entityManagerFactory" }
        assertNotNull(method.getAnnotation(Primary::class.java))
        assertEquals("entityManagerFactory", method.getAnnotation(Bean::class.java).name.single())
    }

    @Test
    fun existingTransactionManagerIsPrimary() {
        val method = TransactionConfig::class.java.declaredMethods.first { it.name == "transactionManager" }
        assertNotNull(method.getAnnotation(Primary::class.java))
    }
}
