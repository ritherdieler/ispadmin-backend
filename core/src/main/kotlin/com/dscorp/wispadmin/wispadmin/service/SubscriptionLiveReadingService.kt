package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.routeros.RouterOsTrafficCounterParser
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
) {
    internal var clock: Clock = Clock.systemUTC()

    fun read(subscriptionId: Int): SubscriptionLiveReadingDto {
        val subscription = subscriptionRepository.findById(subscriptionId).orElse(null)
            ?: return unavailable(subscriptionId)
        val host = subscription.hostDevice ?: return unavailable(subscriptionId)
        return try {
            val queues = mikrotikConnectionService.printOnDevice(host, "/queue/simple")
            val queue = queues.firstOrNull { matchesQueue(it, subscription) }
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

    private fun matchesQueue(queue: Map<String, String>, subscription: Subscription): Boolean {
        val name = queue["name"]
        val target = RouterOsTrafficCounterParser.normalizeTarget(queue["target"])
        val ips = listOfNotNull(
            subscription.ip?.trim()?.takeIf { it.isNotEmpty() },
            subscription.pppoeLastIp?.trim()?.takeIf { it.isNotEmpty() },
        )
        if (target != null && ips.any { it == target }) return true
        if (SimpleQueueNameParser.subscriptionId(name) == subscription.id) return true
        val username = subscription.pppoeUsername?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        return extractPppoeUsername(name).equals(username, ignoreCase = true)
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
