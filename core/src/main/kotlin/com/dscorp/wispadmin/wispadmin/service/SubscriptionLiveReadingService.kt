package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.routeros.RouterOsTrafficCounterParser
import com.dscorp.wispadmin.shared.config.GigafiberEnvironmentProperties
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionLiveReadingDto
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.mikrotik.SimpleQueueNameParser
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

@Service
class SubscriptionLiveReadingService(
    private val subscriptionRepository: SubscriptionRepository,
    private val mikrotikConnectionService: MikroTikConnectionService,
    private val environment: GigafiberEnvironmentProperties,
) {
    internal var clock: Clock = Clock.systemUTC()

    fun read(subscriptionId: Int): SubscriptionLiveReadingDto {
        val subscription = subscriptionRepository.findById(subscriptionId).orElse(null)
            ?: return unavailable(subscriptionId)
        val host = subscription.hostDevice ?: return unavailable(subscriptionId)
        return try {
            val queues = mikrotikConnectionService.printOnDevice(host, "/queue/simple")
            val queue = pickQueue(queues, subscription)
            if (queue != null) {
                return fromQueue(subscriptionId, queue)
            }
            val interfaces = mikrotikConnectionService.printOnDevice(host, "/interface")
            val pppoe = interfaces.firstOrNull { matchesPppoeInterface(it, subscription.pppoeUsername) }
            if (pppoe != null) {
                return fromPppoe(subscriptionId, pppoe)
            }
            unavailable(subscriptionId)
        } catch (_: Exception) {
            unavailable(subscriptionId)
        }
    }

    private fun fromQueue(subscriptionId: Int, queue: Map<String, String>): SubscriptionLiveReadingDto {
        val rate = RouterOsTrafficCounterParser.parseUpDown(queue["rate"]) ?: (0L to 0L)
        val bytes = RouterOsTrafficCounterParser.parseUpDown(queue["bytes"]) ?: (0L to 0L)
        return SubscriptionLiveReadingDto(
            subscriptionId = subscriptionId,
            available = true,
            pppoe = null,
            timestamp = Instant.now(clock).toString(),
            downloadBps = rate.second,
            uploadBps = rate.first,
            rxBytes = bytes.second,
            txBytes = bytes.first,
            source = SOURCE_QUEUE,
        )
    }

    private fun fromPppoe(subscriptionId: Int, iface: Map<String, String>): SubscriptionLiveReadingDto {
        return SubscriptionLiveReadingDto(
            subscriptionId = subscriptionId,
            available = true,
            pppoe = extractPppoeUsername(iface["name"]),
            timestamp = Instant.now(clock).toString(),
            downloadBps = 0,
            uploadBps = 0,
            rxBytes = iface["rx-byte"]?.toLongOrNull() ?: 0L,
            txBytes = iface["tx-byte"]?.toLongOrNull() ?: 0L,
            source = SOURCE_PPPOE,
        )
    }

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
            source = SOURCE_NONE,
        )
    }

    private fun pickQueue(
        queues: List<Map<String, String>>,
        subscription: Subscription,
    ): Map<String, String>? {
        val username = subscription.pppoeUsername?.trim()?.takeIf { it.isNotEmpty() }
        if (username != null) {
            queues.firstOrNull { extractPppoeUsername(it["name"]).equals(username, ignoreCase = true) }
                ?.let { return it }
        }
        val ips = listOfNotNull(
            subscription.ip?.trim()?.takeIf { it.isNotEmpty() },
            subscription.pppoeLastIp?.trim()?.takeIf { it.isNotEmpty() },
        )
        queues.firstOrNull { queue ->
            val target = RouterOsTrafficCounterParser.normalizeTarget(queue["target"])
            target != null && ips.any { it == target }
        }?.let { return it }
        return queues.firstOrNull { matchesOwnedQueue(it, subscription) }
    }

    private fun matchesOwnedQueue(queue: Map<String, String>, subscription: Subscription): Boolean {
        val owner = SimpleQueueNameParser.owner(queue["name"]) ?: return false
        return owner.subscriptionId == subscription.id && owner.envTag == environment.normalizedTag()
    }

    private fun matchesPppoeInterface(iface: Map<String, String>, username: String?): Boolean {
        if (iface["type"] != TYPE_PPPOE_IN) return false
        val expected = username?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        return extractPppoeUsername(iface["name"]).equals(expected, ignoreCase = true)
    }

    companion object {
        const val SOURCE_QUEUE = "QUEUE"
        const val SOURCE_PPPOE = "PPPOE"
        const val SOURCE_NONE = "NONE"
        private const val TYPE_PPPOE_IN = "pppoe-in"
        private val PPPOE_NAME = Regex("^<?pppoe-([^>]+)>?$", RegexOption.IGNORE_CASE)

        fun extractPppoeUsername(name: String?): String? {
            val trimmed = name?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            return PPPOE_NAME.find(trimmed)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
        }
    }
}
