package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.shared.config.GigafiberEnvironmentProperties
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmSecretCipher
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.web.client.RestTemplate
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

@Configuration
@EnableConfigurationProperties(ProvisioningV2Properties::class)
class ProvisioningInfrastructureConfig {
    @Bean
    fun provisioningJournal(@Qualifier("dataSource") source: DataSource, json: ObjectMapper,
        @Qualifier("transactionManager") transactionManager: PlatformTransactionManager) =
        ProvisioningJournal(JdbcTemplate(source), json, transactionManager)

    @Bean
    fun provisioningResourceStore(@Qualifier("dataSource") source: DataSource, cipher: CrmSecretCipher,
        @Qualifier("transactionManager") transactionManager: PlatformTransactionManager) =
        ProvisioningResourceStore(JdbcTemplate(source), cipher, transactionManager)

    @Bean
    fun provisioningControlService(journal: ProvisioningJournal, environment: GigafiberEnvironmentProperties) =
        ProvisioningControlService(journal, environment.normalizedTag().ifBlank { "prod" })
}

@Configuration
@ConditionalOnProperty(prefix = "provisioning", name = ["telemetry-enabled"], havingValue = "true")
class ProvisioningTelemetryConfig {
    @Bean
    fun provisioningOutbox(journal: ProvisioningJournal, json: ObjectMapper,
        @Value("\${provisioning.observability-base-url}") baseUrl: String,
        @Value("\${provisioning.observability-api-key}") apiKey: String): ProvisioningOutbox {
        val factory = SimpleClientHttpRequestFactory().apply { setConnectTimeout(3000); setReadTimeout(10000) }
        val delivery = ProvisioningTelemetryDelivery(RestTemplate(factory), json, baseUrl, apiKey)
        return ProvisioningOutbox(journal, json, delivery::send)
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    fun provisioningOutboxScheduler(outbox: ProvisioningOutbox,
        @Value("\${provisioning.outbox-delay-ms:5000}") delayMs: Long) = ProvisioningOutboxScheduler(outbox, delayMs)
}

class ProvisioningOutboxScheduler(private val outbox: ProvisioningOutbox, private val delayMs: Long) : AutoCloseable {
    private val scheduler = ThreadPoolTaskScheduler().apply {
        poolSize = 1
        setThreadNamePrefix("provisioning-outbox-")
        setRemoveOnCancelPolicy(true)
    }
    fun start() {
        require(delayMs >= 1000)
        scheduler.initialize()
        scheduler.scheduleWithFixedDelay(Runnable { outbox.flush() }, delayMs)
    }
    override fun close() = scheduler.shutdown()
}
