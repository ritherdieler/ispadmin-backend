package com.dscorp.wispadmin.wispadmin

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import javax.persistence.EntityManagerFactory

@Configuration
@EnableTransactionManagement
class TransactionConfig(
    private val entityManagerFactory: EntityManagerFactory
) {

    @Bean
    fun transactionManager(): JpaTransactionManager {
        val transactionManager = JpaTransactionManager()
        transactionManager.entityManagerFactory = entityManagerFactory
        return transactionManager
    }

}