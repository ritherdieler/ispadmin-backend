package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.routeros.RouterOsTrafficCounterParser
import com.dscorp.wispadmin.shared.config.GigafiberEnvironmentProperties
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.usesSimpleQueue
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
            if (!subscription.accessMode.usesSimpleQueue()) {
                return readPppoeInterface(subscriptionId, subscription, host)
            }
            val assignedIps = listOfNotNull(
                subscription.ip?.trim()?.takeIf { it.isNotEmpty() },
                subscription.pppoeLastIp?.trim()?.takeIf { it.isNotEmpty() },
            )
            val livePppoe = findPppoeInterfaceByAssignedIp(host, assignedIps)
            if (livePppoe != null) {
                return fromPppoe(subscriptionId, host, livePppoe)
            }
            val queues = mikrotikConnectionService.printOnDevice(host, "/queue/simple")
            val queue = pickQueue(queues, subscription)
            if (queue != null) {
                return fromQueue(subscriptionId, queue)
            }
            unavailable(subscriptionId)
        } catch (_: Exception) {
            unavailable(subscriptionId)
        }
    }

    private fun readPppoeInterface(
        subscriptionId: Int,
        subscription: Subscription,
        host: NetworkDevice,
    ): SubscriptionLiveReadingDto {
        val interfaces = mikrotikConnectionService.printOnDevice(host, "/interface")
        val pppoe = interfaces.firstOrNull { matchesPppoeInterface(it, subscription.pppoeUsername) }
            ?: return unavailable(subscriptionId)
        return fromPppoe(subscriptionId, host, pppoe)
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

    private fun fromPppoe(
        subscriptionId: Int,
        host: NetworkDevice,
        iface: Map<String, String>,
    ): SubscriptionLiveReadingDto {
        val name = iface["name"].orEmpty()
        val monitor = try {
            mikrotikConnectionService.callOnDevice(
                host,
                MONITOR_TRAFFIC_PATH,
                mapOf("interface" to name, "once" to ""),
            ).firstOrNull()
        } catch (_: Exception) {
            null
        }
        return SubscriptionLiveReadingDto(
            subscriptionId = subscriptionId,
            available = true,
            pppoe = extractPppoeUsername(name),
            timestamp = Instant.now(clock).toString(),
            downloadBps = parseBits(monitor?.get("tx-bits-per-second")),
            uploadBps = parseBits(monitor?.get("rx-bits-per-second")),
            rxBytes = iface["tx-byte"]?.toLongOrNull() ?: 0L,
            txBytes = iface["rx-byte"]?.toLongOrNull() ?: 0L,
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

    private fun findPppoeInterfaceByAssignedIp(
        host: NetworkDevice,
        ips: List<String>,
    ): Map<String, String>? {
        if (ips.isEmpty()) return null
        val sessions = try {
            mikrotikConnectionService.printOnDevice(host, "/ppp/active")
        } catch (_: Exception) {
            return null
        }
        val username = sessions.firstOrNull { session ->
            val address = RouterOsTrafficCounterParser.normalizeTarget(session["address"])
            address != null && ips.any { it == address }
        }?.get("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val interfaces = mikrotikConnectionService.printOnDevice(host, "/interface")
        return interfaces.firstOrNull { matchesPppoeInterface(it, username) }
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
        private const val MONITOR_TRAFFIC_PATH = "/interface/monitor-traffic"
        private val PPPOE_NAME = Regex("^<?pppoe-([^>]+)>?$", RegexOption.IGNORE_CASE)

        fun extractPppoeUsername(name: String?): String? {
            val trimmed = name?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            return PPPOE_NAME.find(trimmed)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
        }

        private fun parseBits(value: String?): Long {
            if (value.isNullOrBlank()) return 0L
            return value.toLongOrNull() ?: value.toDoubleOrNull()?.toLong() ?: 0L
        }
    }
}
