package com.dscorp.wispadmin.wispadmin.config

import com.dscorp.wispadmin.wispadmin.oltclient.OltGatewayHttpClient
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionAcsRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.genieacs.GenieAcsClient
import com.dscorp.wispadmin.wispadmin.service.genieacs.GenieAcsProperties
import com.dscorp.wispadmin.wispadmin.service.genieacs.GenieAcsSubscriptionTagger
import com.dscorp.wispadmin.wispadmin.service.genieacs.SubscriptionAcsLinkService
import com.dscorp.wispadmin.wispadmin.service.genieacs.SubscriptionAcsSyncService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

@Configuration
class SubscriptionAcsLinkConfig {

    @Bean("coreGenieAcsNbiTemplate")
    fun coreGenieAcsNbiTemplate(environment: Environment): RestTemplate {
        val connect = environment.getProperty("genieacs.connect-timeout-ms", Long::class.java, 5_000L)
            .toInt().coerceAtLeast(1000)
        val taskTimeout = environment.getProperty("genieacs.task-timeout-ms", Long::class.java, 30_000L)
        val read = (taskTimeout + 15_000).toInt().coerceAtLeast(5_000)
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(connect)
            setReadTimeout(read)
        }
        return RestTemplate(factory)
    }

    @Bean
    fun subscriptionAcsLinkService(
        subscriptionRepository: SubscriptionRepository,
        acsRepository: SubscriptionAcsRepository,
        gatewayHttp: ObjectProvider<OltGatewayHttpClient>,
        eventPublisher: ApplicationEventPublisher,
        objectMapper: ObjectMapper,
        environment: Environment,
        @Qualifier("coreGenieAcsNbiTemplate") nbi: RestTemplate,
    ): SubscriptionAcsLinkService {
        val properties = GenieAcsProperties().apply {
            nbiBaseUrl = environment.getProperty("genieacs.nbi-base-url", nbiBaseUrl)
            connectTimeoutMs = environment.getProperty("genieacs.connect-timeout-ms", Long::class.java, connectTimeoutMs)
            taskTimeoutMs = environment.getProperty("genieacs.task-timeout-ms", Long::class.java, taskTimeoutMs)
        }
        val client = GenieAcsClient(properties, objectMapper, nbi)
        return SubscriptionAcsLinkService(
            subscriptionRepository = subscriptionRepository,
            client = client,
            syncService = SubscriptionAcsSyncService(acsRepository, client),
            tagger = GenieAcsSubscriptionTagger(client),
            gatewayHttp = gatewayHttp,
            eventPublisher = eventPublisher,
        )
    }
}
