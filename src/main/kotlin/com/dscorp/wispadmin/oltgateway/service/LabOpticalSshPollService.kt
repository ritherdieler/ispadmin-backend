package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.parser.OpticalInfoParser
import com.dscorp.wispadmin.oltgateway.parser.ParsedOpticalInfo
import com.dscorp.wispadmin.oltgateway.ssh.CliBusResult
import com.dscorp.wispadmin.oltgateway.ssh.CliJobType
import com.dscorp.wispadmin.oltgateway.ssh.OltCliBus
import com.dscorp.wispadmin.servicehealth.port.HealthLabOpticalPort
import com.dscorp.wispadmin.servicehealth.port.HealthLabOpticalRefresh
import com.dscorp.wispadmin.servicehealth.port.HealthLabScopePort
import com.dscorp.wispadmin.servicehealth.port.HealthOnuPort
import com.dscorp.wispadmin.servicehealth.port.HealthOnuRef
import com.dscorp.wispadmin.wispadmin.config.GigafiberEnvironmentProperties
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant

data class LabOpticalPollResult(
    val collected: Int = 0,
    val unmapped: Int = 0,
    val error: String? = null,
)

@Service
@ConditionalOnProperty(prefix = "olt.gateway", name = ["enabled"], havingValue = "true")
class LabOpticalSshPollService(
    private val environment: GigafiberEnvironmentProperties,
    private val scope: HealthLabScopePort,
    private val subscriptions: SubscriptionRepository,
    private val onuPort: HealthOnuPort,
    private val cliBus: OltCliBus,
    private val parser: OpticalInfoParser,
    private val publisher: ApplicationEventPublisher,
) : HealthLabOpticalPort {
    private val logger = LoggerFactory.getLogger(LabOpticalSshPollService::class.java)

    fun pollAllLab(): LabOpticalPollResult {
        if (environment.normalizedTag().isBlank()) return LabOpticalPollResult(error = "prod_environment")
        return poll(scope.collectionSubscriptionIds())
    }

    override fun refreshSubscription(subscriptionId: Int): HealthLabOpticalRefresh {
        if (environment.normalizedTag().isBlank()) return HealthLabOpticalRefresh(false, error = "prod_environment")
        if (!scope.collects(subscriptionId)) return HealthLabOpticalRefresh(false, error = "not_lab")
        val result = poll(listOf(subscriptionId))
        return HealthLabOpticalRefresh(
            collected = result.collected > 0,
            unmapped = result.unmapped > 0,
            error = result.error,
        )
    }

    private fun poll(ids: Collection<Int>): LabOpticalPollResult {
        var unmapped = 0
        val targets = LabOpticalTargets.resolve(ids) { id ->
            val sn = subscriptions.findById(id).orElse(null)?.fiberOnu?.sn
            if (sn.isNullOrBlank()) {
                unmapped++
                logger.info("Lab optical SSH unmapped subscription={}", id)
                null
            } else {
                onuPort.findBySn(sn) ?: run {
                    unmapped++
                    logger.info("Lab optical SSH unmapped sn={} subscription={}", sn, id)
                    null
                }
            }
        }
        if (targets.isEmpty()) return LabOpticalPollResult(collected = 0, unmapped = unmapped)
        var collected = 0
        for ((oltId, group) in targets.groupBy { it.onu.oltId }) {
            if (oltId == null) {
                unmapped += group.size
                continue
            }
            val rows = mutableListOf<OltSignalPollService.OpticalRow>()
            for (target in group) {
                val parsed = readOptical(target.onu) ?: continue
                rows += OltSignalPollService.OpticalRow(target.onu.board, target.onu.port, parsed)
                collected++
            }
            if (rows.isNotEmpty()) {
                publisher.publishEvent(OltOpticalObservation(oltId, Instant.now(), rows))
            }
        }
        return LabOpticalPollResult(collected = collected, unmapped = unmapped)
    }

    private fun readOptical(onu: HealthOnuRef): ParsedOpticalInfo? {
        return try {
            when (val result = cliBus.execute(CliJobType.ADHOC) { session ->
                session.execute("interface gpon 0/${onu.board}")
                val output = session.execute("display ont optical-info ${onu.port} ${onu.onuIndex}")
                session.execute("quit")
                parser.parse(output, onu.onuIndex)
            }) {
                is CliBusResult.Ok -> result.value
                is CliBusResult.Skipped -> {
                    logger.info("Lab optical SSH skipped onu={} reason={}", onu.sn, result.reason)
                    null
                }
            }
        } catch (ex: Exception) {
            logger.warn("Lab optical SSH failed onu={}: {}", onu.sn, ex.message)
            null
        }
    }
}
