package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.shared.config.GigafiberEnvironmentProperties
import com.dscorp.wispadmin.wispadmin.acsclient.AcsCpeCoreClient
import com.dscorp.wispadmin.wispadmin.extensions.executeCommand
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayOnuActivationClient
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
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
    ) = ProvisioningExecutor(journal, listOf(
        ValidateProvisioningStageHandler(subscriptions),
        MikrotikProvisioningStageHandler(subscriptions, cipher) { device, block -> device.executeCommand(block) },
        OltProvisioningStageHandler(subscriptions, gateway, json),
        OmciProvisioningStageHandler(gateway, json),
        AcsContactProvisioningStageHandler(acs, json),
        InternetProvisioningStageHandler(subscriptions, cipher, acs, json),
        WifiProvisioningStageHandler(acs, json),
        VerifyProvisioningStageHandler(),
    ), resources, Clock.systemUTC())

    @Bean
    fun provisioningSubscriptionStatusProjector(journal: ProvisioningJournal, subscriptions: SubscriptionRepository) =
        ProvisioningSubscriptionStatusProjector(journal, subscriptions)

    @Bean(initMethod = "start", destroyMethod = "close")
    fun provisioningRecoveryLoop(journal: ProvisioningJournal, executor: ProvisioningExecutor,
        environment: GigafiberEnvironmentProperties, statuses: ProvisioningSubscriptionStatusProjector) = ProvisioningRecoveryLoop(
        ProvisioningRecoveryScheduler(
            journal,
            executor,
            Clock.systemUTC(),
            environment.normalizedTag().ifBlank { "prod" },
            statuses,
        ),
    )
}

class ProvisioningRecoveryLoop(private val recovery: ProvisioningRecoveryScheduler) : AutoCloseable {
    private val scheduler = ThreadPoolTaskScheduler().apply { poolSize = 1; setThreadNamePrefix("provisioning-") }
    fun start() { scheduler.initialize(); scheduler.scheduleWithFixedDelay(Runnable { recovery.recover() }, 5_000) }
    override fun close() = scheduler.shutdown()
}
