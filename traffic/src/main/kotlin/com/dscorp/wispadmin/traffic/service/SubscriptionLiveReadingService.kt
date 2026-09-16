package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.routeros.RouterOsTrafficCounterParser
import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import com.dscorp.wispadmin.routeros.port.MikrotikClient
import com.dscorp.wispadmin.routeros.port.MikrotikDeviceRef
import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.dscorp.wispadmin.traffic.dto.SubscriptionLiveReadingDto
import com.dscorp.wispadmin.traffic.repository.TrafficRouterRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

@Service
class SubscriptionLiveReadingService(
    private val routerRepository: TrafficRouterRepository,
    @Qualifier("trafficPollMikrotikClient")
    private val mikrotikClient: MikrotikClient,
    private val routerOsClientProperties: RouterOsClientProperties,
) {
    internal var clock: Clock = Clock.systemUTC()

    fun read(identity: LiveReadingIdentity): SubscriptionLiveReadingDto {
        val hostDeviceId = identity.hostDeviceId ?: return unavailable(identity.subscriptionId)
        val router = routerRepository.findById(hostDeviceId).orElse(null)
            ?: return unavailable(identity.subscriptionId)
        val deviceRef = MikrotikDeviceRef(
            id = router.id.toString(),
            host = router.host,
            port = routerOsClientProperties.rest.port,
            username = router.username,
            password = router.password,
        )
        return runCatching {
            mikrotikClient.withSession(deviceRef) { session ->
                if (!identity.usesSimpleQueue()) {
                    readPppoeInterface(identity, session)
                } else {
                    readSimpleQueueOrLeftoverPppoe(identity, session)
                }
            }
        }.getOrElse { unavailable(identity.subscriptionId) }
    }

    private fun readSimpleQueueOrLeftoverPppoe(
        identity: LiveReadingIdentity,
        session: MikrotikSession,
    ): SubscriptionLiveReadingDto {
        val assignedIps = listOfNotNull(
            identity.ip?.trim()?.takeIf { it.isNotEmpty() },
            identity.pppoeLastIp?.trim()?.takeIf { it.isNotEmpty() },
        )
        val livePppoe = findPppoeInterfaceByAssignedIp(session, assignedIps)
        if (livePppoe != null) {
            return fromPppoe(identity.subscriptionId, session, livePppoe)
        }
        val queues = session.print("/queue/simple")
        val queue = pickQueue(queues, identity)
        if (queue != null) {
            return fromQueue(identity.subscriptionId, queue)
        }
        return unavailable(identity.subscriptionId)
    }

    private fun readPppoeInterface(
        identity: LiveReadingIdentity,
        session: MikrotikSession,
    ): SubscriptionLiveReadingDto {
        val interfaces = session.print("/interface")
        val pppoe = interfaces.firstOrNull { matchesPppoeInterface(it, identity.pppoeUsername) }
            ?: return unavailable(identity.subscriptionId)
        return fromPppoe(identity.subscriptionId, session, pppoe)
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
        session: MikrotikSession,
        iface: Map<String, String>,
    ): SubscriptionLiveReadingDto {
        val name = iface["name"].orEmpty()
        val monitor = runCatching {
            session.call(MONITOR_TRAFFIC_PATH, mapOf("interface" to name, "once" to "")).firstOrNull()
        }.getOrNull()
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
        identity: LiveReadingIdentity,
    ): Map<String, String>? {
        val username = identity.pppoeUsername?.trim()?.takeIf { it.isNotEmpty() }
        if (username != null) {
            queues.firstOrNull { extractPppoeUsername(it["name"]).equals(username, ignoreCase = true) }
                ?.let { return it }
        }
        val ips = listOfNotNull(
            identity.ip?.trim()?.takeIf { it.isNotEmpty() },
            identity.pppoeLastIp?.trim()?.takeIf { it.isNotEmpty() },
        )
        queues.firstOrNull { queue ->
            val target = RouterOsTrafficCounterParser.normalizeTarget(queue["target"])
            target != null && ips.any { it == target }
        }?.let { return it }
        return queues.firstOrNull { matchesOwnedQueue(it, identity) }
    }

    private fun matchesOwnedQueue(queue: Map<String, String>, identity: LiveReadingIdentity): Boolean {
        val owner = SimpleQueueNameParser.owner(queue["name"]) ?: return false
        val envTag = identity.envTag.orEmpty()
        return owner.subscriptionId == identity.subscriptionId && owner.envTag == envTag
    }

    private fun findPppoeInterfaceByAssignedIp(
        session: MikrotikSession,
        ips: List<String>,
    ): Map<String, String>? {
        if (ips.isEmpty()) return null
        val sessions = runCatching { session.print("/ppp/active") }.getOrElse { return null }
        val username = sessions.firstOrNull { row ->
            val address = RouterOsTrafficCounterParser.normalizeTarget(row["address"])
            address != null && ips.any { it == address }
        }?.get("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val interfaces = session.print("/interface")
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
