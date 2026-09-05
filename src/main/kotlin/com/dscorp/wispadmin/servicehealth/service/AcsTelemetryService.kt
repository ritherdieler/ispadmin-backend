package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.config.ServiceHealthScope
import com.dscorp.wispadmin.servicehealth.domain.HealthCursor
import com.dscorp.wispadmin.servicehealth.domain.Quality
import com.dscorp.wispadmin.servicehealth.domain.TelemetryRun
import com.dscorp.wispadmin.servicehealth.domain.WifiCurrent
import com.dscorp.wispadmin.servicehealth.port.HealthCpePort
import com.dscorp.wispadmin.servicehealth.repository.HealthCursorRepository
import com.dscorp.wispadmin.servicehealth.repository.TelemetryRunRepository
import com.dscorp.wispadmin.servicehealth.repository.WifiCurrentRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

@Service
class AcsTelemetryService(
    private val properties: ServiceHealthProperties,
    private val scope: ServiceHealthScope,
    private val cpe: ObjectProvider<HealthCpePort>,
    private val subscriptions: SubscriptionRepository,
    private val current: WifiCurrentRepository,
    private val cursors: HealthCursorRepository,
    private val runs: TelemetryRunRepository,
    private val tx: TransactionTemplate,
) {
    @Scheduled(fixedDelayString="\${service.health.acs-interval-ms:120000}",initialDelayString="\${service.health.acs-initial-delay-ms:45000}")
    fun poll() {
        if(!properties.enabled || !properties.acsEnabled) return
        val port = cpe.ifAvailable ?: return
        val collectIds=scope.collectionSubscriptionIds()
        if(collectIds.isEmpty()) return
        tx.executeWithoutResult {
            if(cursors.lock("acs-watcher") == null) return@executeWithoutResult
            val now=Instant.now()
            val run=runs.save(TelemetryRun(source="ACS",equipmentKey="gateway-cpe",startedAt=now))
            try {
                for (id in collectIds) {
                    if (!scope.collects(id)) continue
                    val sn = subscriptions.findById(id).orElse(null)?.fiberOnu?.sn ?: continue
                    val telemetry = port.telemetry(sn)
                    if (telemetry == null) {
                        run.missingCount++
                        continue
                    }
                    run.readCount++
                    val status=current.findById(id).orElse(WifiCurrent(subscriptionId=id))
                    status.deviceId = telemetry.uniqueExternalId ?: sn
                    status.model = telemetry.productClass
                    status.informAt = telemetry.lastInformAt
                    status.observedAt = now
                    status.qualityStatus = if (telemetry.lastInformAt != null) Quality.FRESH else Quality.MISSING
                    current.save(status)
                    run.writtenCount++
                }
                run.completedAt=Instant.now()
            } catch (_: Exception) {
                run.qualityStatus=Quality.ERROR; run.errorCount++; run.errorReason="CPE_GATEWAY_READ_FAILED"; run.completedAt=Instant.now()
            }
            runs.save(run)
        }
    }
}
