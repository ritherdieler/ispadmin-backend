package com.dscorp.wispadmin.wispadmin.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Configuration
@EnableConfigurationProperties(SmartMapRoutingProperties::class)
class SmartMapRoutingConfiguration(
    private val routingProperties: SmartMapRoutingProperties,
) {

    @Bean("mapboxWebClient")
    fun mapboxWebClient(): WebClient {
        val timeout = Duration.ofMillis(routingProperties.mapbox.timeoutMs)
        val httpClient = HttpClient.create()
            .responseTimeout(timeout)

        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024)
            }
            .build()
    }
}
