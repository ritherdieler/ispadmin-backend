package com.dscorp.wispadmin.wispadmin.genieacs

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.ExchangeFilterFunctions
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Configuration
@EnableConfigurationProperties(GenieAcsProperties::class)
class GenieAcsConfiguration(
    private val properties: GenieAcsProperties,
) {

    @Bean("genieAcsWebClient")
    fun genieAcsWebClient(): WebClient {
        val timeout = Duration.ofMillis(properties.timeoutMs)
        val httpClient = HttpClient.create().responseTimeout(timeout)
        val builder = WebClient.builder()
            .baseUrl(properties.baseUrl.trimEnd('/'))
            .clientConnector(ReactorClientHttpConnector(httpClient))

        if (properties.username.isNotBlank()) {
            builder.filter(
                ExchangeFilterFunctions.basicAuthentication(
                    properties.username,
                    properties.password
                )
            )
        }

        return builder.build()
    }

    @Bean
    fun genieAcsClient(@Qualifier("genieAcsWebClient") genieAcsWebClient: WebClient): GenieAcsClient =
        GenieAcsClient(genieAcsWebClient)
}
