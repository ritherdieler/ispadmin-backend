package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.observability.port.ObservabilityReporter
import com.dscorp.wispadmin.observability.port.ReportedEvent
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
                val queues = loadQueueOccupancy(preferredPool.hostDevice)
                val collision = collisionOf(preferred, activeIps, queues, preferredPool.hostDevice?.id)
                if (collision == null) {
                    return preferred to preferredPool
                }
                reportCollision(collision)
            }
        }

        for (ipPool in ipPools) {
            val base = ipPool.ipSegment.getBaseIpFromRange()
            val queues = loadQueueOccupancy(ipPool.hostDevice)
            val occupiedOctets = activeIps
                .filter { it.startsWith(base) }
                .mapNotNull { it.substringAfterLast('.').toIntOrNull() }
                .toSet()
            val start = nextStartOctet(occupiedOctets)
            val ordered = (start..IP_RANGE.last) + (IP_RANGE.first until start)
            for (octet in ordered) {
                val ip = base + octet
                if (ip in activeIps) continue
                val collision = unexpectedCollision(ip, queues, ipPool.hostDevice?.id)
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
        queues: Map<String, QueueOccupancy>,
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
                queueName = queues[ip]?.name
            )
        }
        val queue = queues[ip] ?: return null
        return IpCollision(
            ip = ip,
            reason = REASON_MIKROTIK_QUEUE,
            hostDeviceId = hostDeviceId,
            conflictingSubscriptionId = SimpleQueueNameParser.subscriptionId(queue.name),
            queueName = queue.name
        )
    }

    private fun collisionOf(
        ip: String,
        activeIps: Set<String>,
        queues: Map<String, QueueOccupancy>,
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
                queueName = queues[ip]?.name
            )
        }
        return unexpectedCollision(ip, queues, hostDeviceId)
    }

    private fun loadQueueOccupancy(hostDevice: NetworkDevice?): Map<String, QueueOccupancy> {
        if (hostDevice == null) return emptyMap()
        return runCatching {
            val rows = mutableListOf<Map<String, String>>()
            mikrotikService.executeOnDevice(hostDevice) { session ->
                rows.addAll(session.print("/queue/simple", emptyMap()))
            }
            val occupancy = linkedMapOf<String, QueueOccupancy>()
            rows.forEach { row ->
                val target = row["target"] ?: return@forEach
                val ip = target.substringBefore("/").trim()
                if (ip.isEmpty()) return@forEach
                occupancy[ip] = QueueOccupancy(ip = ip, name = row["name"].orEmpty())
            }
            occupancy
        }.getOrElse { error ->
            logger.warn("No se pudieron leer simple queues del host {}: {}", hostDevice.id, error.message)
            emptyMap()
        }
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

    private data class IpCollision(
        val ip: String,
        val reason: String,
        val hostDeviceId: Int?,
        val conflictingSubscriptionId: Int?,
        val queueName: String?
    )

    companion object {
        private val IP_RANGE = 10..250
        const val EVENT_TYPE = "ip_collision"
        const val ERROR_TYPE = "IpCollision"
        const val REASON_ACTIVE_SUBSCRIPTION = "active_subscription"
        const val REASON_MIKROTIK_QUEUE = "mikrotik_queue"
        const val SOURCE_AUTO = "auto"
    }
}
