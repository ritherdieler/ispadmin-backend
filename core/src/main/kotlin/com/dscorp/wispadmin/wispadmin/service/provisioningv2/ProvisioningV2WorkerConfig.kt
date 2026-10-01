package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.shared.config.GigafiberEnvironmentProperties
import com.dscorp.wispadmin.wispadmin.acsclient.AcsCpeCoreClient
import com.dscorp.wispadmin.wispadmin.extensions.executeCommand
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayOnuActivationClient
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.FirebaseStorageService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmSecretCipher
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.time.Clock

/** Enabled only after migrations, provision publication and lab approval. */
@Configuration
@ConditionalOnProperty(prefix = "provisioning", name = ["worker-enabled"], havingValue = "true")
class ProvisioningV2WorkerConfig {
    @Bean
    fun provisioningExecutor(
        journal: ProvisioningJournal,
        resources: ProvisioningResourceStore,
        subscriptions: SubscriptionRepository,
        cipher: CrmSecretCipher,
        gateway: GatewayOnuActivationClient,
        acs: AcsCpeCoreClient,
        json: ObjectMapper,
        storage: FirebaseStorageService,
    ) = ProvisioningExecutor(journal, listOf(
        ValidateProvisioningStageHandler(subscriptions),
        MikrotikProvisioningStageHandler(subscriptions, cipher) { device, block -> device.executeCommand(block) },
        OltProvisioningStageHandler(subscriptions, gateway, json),
        AcsContactProvisioningStageHandler(acs, json),
        InternetProvisioningStageHandler(subscriptions, cipher, acs, json),
        WifiProvisioningStageHandler(acs, json),
        WanCleanupProvisioningStageHandler(acs, json),
        VerifyProvisioningStageHandler(),
    ), resources, Clock.systemUTC(), storage)

    @Bean
    fun provisioningSubscriptionStatusProjector(journal: ProvisioningJournal, subscriptions: SubscriptionRepository) =
        ProvisioningSubscriptionStatusProjector(journal, subscriptions)

    @Bean(initMethod = "start", destroyMethod = "close")
    fun provisioningRecoveryLoop(journal: ProvisioningJournal, executor: ProvisioningExecutor,
        environment: GigafiberEnvironmentProperties, statuses: ProvisioningSubscriptionStatusProjector,
        heartbeat: ProvisioningWorkerHeartbeat,
        @org.springframework.beans.factory.annotation.Value("\${provisioning.worker-threads:4}") workerThreads: Int,
    ): ProvisioningRecoveryLoop {
        val workers = java.util.concurrent.Executors.newFixedThreadPool(workerThreads.coerceIn(1, 16)) { runnable ->
            Thread(runnable, "provisioning-worker").apply { isDaemon = true }
        }
        return ProvisioningRecoveryLoop(
            ProvisioningRecoveryScheduler(
                journal,
                executor,
                Clock.systemUTC(),
                environment.normalizedTag().ifBlank { "prod" },
                statuses,
                heartbeat,
                workers,
            ),
            workers,
        )
    }
}

@Configuration
@ConditionalOnProperty(prefix = "provisioning", name = ["worker-enabled", "preauth-async"], havingValue = "true")
class ProvisioningPreauthorizationWorkerConfig {
    @Bean(initMethod = "start", destroyMethod = "close")
    fun provisioningPreauthorizationLoop(service: OnuRegistrationOperationService) = ProvisioningPreauthorizationLoop(service)
}

class ProvisioningPreauthorizationLoop(private val service: OnuRegistrationOperationService) : AutoCloseable {
    private val scheduler = ThreadPoolTaskScheduler().apply { poolSize = 1; setThreadNamePrefix("provisioning-preauth-") }
    fun start() {
        scheduler.initialize()
        scheduler.scheduleWithFixedDelay(Runnable { service.processPendingPreauthorizations(java.time.Instant.now()) }, 3_000)
    }
    override fun close() = scheduler.shutdown()
}

class ProvisioningRecoveryLoop(
    private val recovery: ProvisioningRecoveryScheduler,
    private val workers: java.util.concurrent.ExecutorService? = null,
) : AutoCloseable {
    private val scheduler = ThreadPoolTaskScheduler().apply { poolSize = 1; setThreadNamePrefix("provisioning-") }
    fun start() { scheduler.initialize(); scheduler.scheduleWithFixedDelay(Runnable { recovery.recover() }, 5_000) }
    override fun close() {
        scheduler.shutdown()
        workers?.shutdown()
    }
}
