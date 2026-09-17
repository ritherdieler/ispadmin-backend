package com.dscorp.wispadmin.wispadmin.config

import com.dscorp.wispadmin.wispadmin.TransactionConfig
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Primary

class CoreJpaConfigTest {

    @Test
    fun entityManagerFactoryIsPrimaryAndNamedForEnableJpaRepositories() {
        val method = CoreJpaConfig::class.java.declaredMethods.first { it.name == "entityManagerFactory" }
        assertNotNull(method.getAnnotation(Primary::class.java))
        assertEquals("entityManagerFactory", method.getAnnotation(Bean::class.java).name.single())
    }

    @Test
    fun coreFlywayBeanMigratesBeforeEntityManagerFactory() {
        val flyway = CoreJpaConfig::class.java.declaredMethods.first { it.name == "coreFlyway" }
        assertEquals("coreFlyway", flyway.getAnnotation(Bean::class.java).name.single())
        assertEquals(Flyway::class.java, flyway.returnType)

        val emf = CoreJpaConfig::class.java.declaredMethods.first { it.name == "entityManagerFactory" }
        val dependsOn = emf.getAnnotation(DependsOn::class.java)
        assertNotNull(dependsOn)
        assertTrue(dependsOn.value.contains("coreFlyway"), dependsOn.value.joinToString())
        assertTrue(
            emf.parameters.any { parameter ->
                parameter.getAnnotation(Qualifier::class.java)?.value == "coreFlyway"
            },
        )
    }

    @Test
    fun existingTransactionManagerIsPrimary() {
        val method = TransactionConfig::class.java.declaredMethods.first { it.name == "transactionManager" }
        assertNotNull(method.getAnnotation(Primary::class.java))
    }
}
