package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.shared.config.GigafiberEnvironmentProperties
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionLiveReadingDto
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.trafficclient.TrafficHttpClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant

@Service
class SubscriptionLiveReadingService(
    private val subscriptionRepository: SubscriptionRepository,
    private val trafficHttpClient: TrafficHttpClient,
    private val environment: GigafiberEnvironmentProperties,
    private val objectMapper: ObjectMapper,
) {
    internal var clock: Clock = Clock.systemUTC()

    fun read(subscriptionId: Int): SubscriptionLiveReadingDto {
        val subscription = subscriptionRepository.findById(subscriptionId).orElse(null)
            ?: return unavailable(subscriptionId)
        val hostId = subscription.hostDevice?.id ?: return unavailable(subscriptionId)
        return runCatching {
            val body = trafficHttpClient.getJson(
                "/api/traffic/v1/by-subscription/$subscriptionId/live-readings",
                queryString(subscription, hostId),
            ).body ?: return@runCatching unavailable(subscriptionId)
            parse(body)
        }.getOrElse { unavailable(subscriptionId) }
    }

    private fun parse(body: String): SubscriptionLiveReadingDto {
        val node = objectMapper.readTree(body)
        val pppoeNode = node.get("pppoe")
        return SubscriptionLiveReadingDto(
            subscriptionId = node.path("subscriptionId").asInt(),
            available = node.path("available").asBoolean(),
            pppoe = if (pppoeNode == null || pppoeNode.isNull) null else pppoeNode.asText(),
            timestamp = node.path("timestamp").asText(),
            downloadBps = node.path("downloadBps").asLong(),
            uploadBps = node.path("uploadBps").asLong(),
            rxBytes = node.path("rxBytes").asLong(),
            txBytes = node.path("txBytes").asLong(),
            source = node.path("source").asText(),
        )
    }

    private fun queryString(subscription: Subscription, hostDeviceId: Int): String {
        val parts = mutableListOf(
            "accessMode=${enc(subscription.accessMode.name)}",
            "hostDeviceId=$hostDeviceId",
            "envTag=${enc(environment.normalizedTag())}",
        )
        subscription.ip?.trim()?.takeIf { it.isNotEmpty() }?.let { parts += "ip=${enc(it)}" }
        subscription.pppoeLastIp?.trim()?.takeIf { it.isNotEmpty() }?.let { parts += "pppoeLastIp=${enc(it)}" }
        subscription.pppoeUsername?.trim()?.takeIf { it.isNotEmpty() }?.let { parts += "pppoeUsername=${enc(it)}" }
        return parts.joinToString("&")
    }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun unavailable(subscriptionId: Int): SubscriptionLiveReadingDto {
        return SubscriptionLiveReadingDto(
            subscriptionId = subscriptionId,
            available = false,
            pppoe = null,
            timestamp = Instant.now(clock).toString(),
            downloadBps = 0,
            uploadBps = 0,
            rxBytes = 0,
            txBytes = 0,
            source = "NONE",
        )
    }
}
