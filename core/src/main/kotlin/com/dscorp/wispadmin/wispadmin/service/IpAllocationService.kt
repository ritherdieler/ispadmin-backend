package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.observability.ObservabilityReporter
import com.dscorp.wispadmin.wispadmin.observability.ReportedEvent
import com.dscorp.wispadmin.wispadmin.data.model.IpPool
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.extensions.getBaseIpFromRange
import com.dscorp.wispadmin.wispadmin.repository.IpPoolRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.mikrotik.IMikroTikService
import com.dscorp.wispadmin.wispadmin.service.mikrotik.SimpleQueueNameParser
import com.dscorp.wispadmin.wispadmin.util.isValidIpAddress
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class IpAllocationService(
    private val ipPoolRepository: IpPoolRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val mikrotikService: IMikroTikService,
    private val observabilityReporter: ObservabilityReporter
) {
    private val logger = LoggerFactory.getLogger(IpAllocationService::class.java)

    fun allocate(hostDeviceId: Int? = null, preferredIp: String? = null): Pair<String, IpPool> {
        val eligiblePools = ipPoolRepository.findAllEligiblePools()
        val ipPools = if (hostDeviceId != null) {
            eligiblePools.filter { it.hostDevice?.id == hostDeviceId }.ifEmpty { eligiblePools }
        } else {
            eligiblePools
        }
        if (ipPools.isEmpty()) {
            throw IllegalStateException("No more ips available")
        }

        val activeIps = subscriptionRepository.findActiveIps()
            .mapNotNull { it?.trim()?.takeIf { ip -> ip.isNotEmpty() } }
            .toSet()

        val preferred = preferredIp?.trim()?.takeIf { it.isNotEmpty() }
        if (preferred != null) {
            require(preferred.isValidIpAddress()) { "La IP del cliente no es válida" }
            val preferredPool = ipPools.firstOrNull { pool ->
                preferred.startsWith(pool.ipSegment.getBaseIpFromRange())
            } ?: eligiblePools.firstOrNull { pool ->
                preferred.startsWith(pool.ipSegment.getBaseIpFromRange())
            }
            if (preferredPool != null) {
                val occupancy = loadDeviceOccupancy(preferredPool.hostDevice)
                val collision = collisionOf(preferred, activeIps, occupancy, preferredPool.hostDevice?.id)
                if (collision == null) {
                    return preferred to preferredPool
                }
                reportCollision(collision)
            }
        }

        for (ipPool in ipPools) {
            val base = ipPool.ipSegment.getBaseIpFromRange()
            val occupancy = loadDeviceOccupancy(ipPool.hostDevice)
            val occupiedOctets = activeIps
                .filter { it.startsWith(base) }
                .mapNotNull { it.substringAfterLast('.').toIntOrNull() }
                .toSet()
            val start = nextStartOctet(occupiedOctets)
            val ordered = (start..IP_RANGE.last) + (IP_RANGE.first until start)
            for (octet in ordered) {
                val ip = base + octet
                if (ip in activeIps) continue
                val collision = unexpectedCollision(ip, occupancy, ipPool.hostDevice?.id)
                if (collision != null) {
                    reportCollision(collision)
                    continue
                }
                return ip to ipPool
            }
        }

        throw IllegalStateException("No more ips available")
    }

    fun reportCollision(
        ip: String,
        reason: String,
        hostDeviceId: Int?,
        conflictingSubscriptionId: Int? = null,
        queueName: String? = null
    ) {
        reportCollision(
            IpCollision(
                ip = ip,
                reason = reason,
                hostDeviceId = hostDeviceId,
                conflictingSubscriptionId = conflictingSubscriptionId,
                queueName = queueName
            )
        )
    }

    private fun nextStartOctet(occupiedOctets: Set<Int>): Int {
        if (occupiedOctets.isEmpty()) return IP_RANGE.first
        val candidate = (occupiedOctets.maxOrNull() ?: (IP_RANGE.first - 1)) + 1
        return if (candidate in IP_RANGE) candidate else IP_RANGE.first
    }

    private fun unexpectedCollision(
        ip: String,
        occupancy: DeviceOccupancy,
        hostDeviceId: Int?
    ): IpCollision? {
        if (subscriptionRepository.existsByIpAndServiceStatus(ip, ServiceStatus.ACTIVE)) {
            val conflictingId = subscriptionRepository
                .findByIpAndServiceStatus(ip, ServiceStatus.ACTIVE)
                .firstOrNull()
                ?.id
            return IpCollision(
                ip = ip,
                reason = REASON_ACTIVE_SUBSCRIPTION,
                hostDeviceId = hostDeviceId,
                conflictingSubscriptionId = conflictingId,
                queueName = occupancy.queues[ip]?.name
            )
        }
        occupancy.queues[ip]?.let { queue ->
            return IpCollision(
                ip = ip,
                reason = REASON_MIKROTIK_QUEUE,
                hostDeviceId = hostDeviceId,
                conflictingSubscriptionId = SimpleQueueNameParser.subscriptionId(queue.name),
                queueName = queue.name
            )
        }
        occupancy.pppoeSecrets[ip]?.let { username ->
            return IpCollision(
                ip = ip,
                reason = REASON_PPPOE_SECRET,
                hostDeviceId = hostDeviceId,
                conflictingSubscriptionId = null,
                queueName = null,
                pppoeUsername = username
            )
        }
        return null
    }

    private fun collisionOf(
        ip: String,
        activeIps: Set<String>,
        occupancy: DeviceOccupancy,
        hostDeviceId: Int?
    ): IpCollision? {
        if (ip in activeIps) {
            val conflictingId = subscriptionRepository
                .findByIpAndServiceStatus(ip, ServiceStatus.ACTIVE)
                .firstOrNull()
                ?.id
            return IpCollision(
                ip = ip,
                reason = REASON_ACTIVE_SUBSCRIPTION,
                hostDeviceId = hostDeviceId,
                conflictingSubscriptionId = conflictingId,
                queueName = occupancy.queues[ip]?.name
            )
        }
        return unexpectedCollision(ip, occupancy, hostDeviceId)
    }

    private fun loadDeviceOccupancy(hostDevice: NetworkDevice?): DeviceOccupancy {
        if (hostDevice == null) return DeviceOccupancy()
        val queueRows = mutableListOf<Map<String, String>>()
        val secretRows = mutableListOf<Map<String, String>>()

        runCatching {
            mikrotikService.executeOnDevice(hostDevice) { session ->
                queueRows.addAll(session.print("/queue/simple", emptyMap()))
                runCatching {
                    secretRows.addAll(session.print("/ppp/secret", emptyMap()))
                }.onFailure { error ->
                    logger.warn(
                        "No se pudieron leer ppp secrets del host {}: {}",
                        hostDevice.id,
                        error.message
                    )
                }
            }
        }.onFailure { error ->
            logger.warn("No se pudieron leer simple queues del host {}: {}", hostDevice.id, error.message)
            return DeviceOccupancy()
        }

        val queues = linkedMapOf<String, QueueOccupancy>()
        queueRows.forEach { row ->
            val target = row["target"] ?: return@forEach
            val ip = target.substringBefore("/").trim()
            if (ip.isEmpty()) return@forEach
            queues[ip] = QueueOccupancy(ip = ip, name = row["name"].orEmpty())
        }

        val secrets = linkedMapOf<String, String>()
        secretRows.forEach { row ->
            val ip = row["remote-address"]?.substringBefore("/")?.trim().orEmpty()
            if (ip.isEmpty()) return@forEach
            secrets[ip] = row["name"].orEmpty()
        }

        return DeviceOccupancy(queues = queues, pppoeSecrets = secrets)
    }

    private fun reportCollision(collision: IpCollision) {
        runCatching {
            observabilityReporter.report(
                ReportedEvent(
                    eventType = EVENT_TYPE,
                    platform = "backend",
                    severity = "warning",
                    message = "Colision IP ${collision.ip} (${collision.reason})",
                    errorType = ERROR_TYPE,
                    stacktrace = null,
                    tags = mapOf(
                        "ip" to collision.ip,
                        "hostDeviceId" to collision.hostDeviceId,
                        "reason" to collision.reason,
                        "conflictingSubscriptionId" to collision.conflictingSubscriptionId,
                        "queueName" to collision.queueName,
                        "pppoeUsername" to collision.pppoeUsername,
                        "source" to SOURCE_AUTO
                    )
                )
            )
        }.onFailure { error ->
            logger.warn("No se pudo reportar ip_collision para {}: {}", collision.ip, error.message)
        }
    }

    private data class QueueOccupancy(
        val ip: String,
        val name: String
    )

    private data class DeviceOccupancy(
        val queues: Map<String, QueueOccupancy> = emptyMap(),
        val pppoeSecrets: Map<String, String> = emptyMap()
    )

    private data class IpCollision(
        val ip: String,
        val reason: String,
        val hostDeviceId: Int?,
        val conflictingSubscriptionId: Int?,
        val queueName: String?,
        val pppoeUsername: String? = null
    )

    companion object {
        private val IP_RANGE = 10..250
        const val EVENT_TYPE = "ip_collision"
        const val ERROR_TYPE = "IpCollision"
        const val REASON_ACTIVE_SUBSCRIPTION = "active_subscription"
        const val REASON_MIKROTIK_QUEUE = "mikrotik_queue"
        const val REASON_PPPOE_SECRET = "pppoe_secret"
        const val SOURCE_AUTO = "auto"
    }
}
