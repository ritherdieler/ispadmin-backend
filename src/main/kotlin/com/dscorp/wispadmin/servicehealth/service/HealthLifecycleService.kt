package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.domain.HealthCursor
import com.dscorp.wispadmin.servicehealth.repository.HealthCursorRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Service
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.annotation.Transactional
import org.springframework.dao.DataIntegrityViolationException
import javax.persistence.EntityManager
import java.time.Instant
import java.time.Duration

@Service
class HealthLifecycleService(private val properties: ServiceHealthProperties, private val cursors: HealthCursorRepository,
                             private val em: EntityManager): ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        if(properties.enabled && (properties.acsEnabled || properties.actionsEnabled)) {
            require(properties.stationHmacKey.toByteArray().size>=32) { "SERVICE_HEALTH_STATION_HMAC_KEY (32+ bytes) requerido para telemetría/acciones" }
        }
        for(key in listOf("acs-watcher","traffic-consumer","evaluation","actions","blast-radius","olt-events")) {
            if(!cursors.existsById(key)) try { cursors.saveAndFlush(HealthCursor(cursorKey=key,observedAt=if(key=="olt-events") Instant.now() else null)) }
            catch (_: DataIntegrityViolationException) { /* Another instance seeded the same lock. */ }
        }
    }
    @Scheduled(cron="\${service.health.retention-cron:0 15 4 * * *}",zone="America/Lima")
    @Transactional
    fun purge() {
        if(!properties.enabled) return
        val now=Instant.now()
        fun purge(entity: String, field: String, days: Long) {
            em.createQuery("delete from $entity e where e.$field < :cutoff").setParameter("cutoff",now.minus(Duration.ofDays(days.coerceAtLeast(1)))).executeUpdate()
        }
        purge("WifiStationSample","observedAt",properties.stationRetentionDays)
        purge("WifiCountSample","informAt",properties.countRetentionDays)
        purge("OpticalSample","observedAt",properties.opticalRetentionDays)
        purge("OnuStateEvent","observedAt",properties.opticalRetentionDays)
        purge("TelemetryRun","startedAt",properties.runRetentionDays)
        val cutoff=now.minus(Duration.ofDays(properties.eventRetentionDays))
        em.createQuery("delete from EvidenceLink l where l.healthEventId in (select e.id from HealthEvent e where e.eventStatus <> 'OPEN' and e.endedAt < :cutoff)")
            .setParameter("cutoff",cutoff).executeUpdate()
        em.createQuery("delete from HealthEvent e where e.eventStatus <> 'OPEN' and e.endedAt < :cutoff").setParameter("cutoff",cutoff).executeUpdate()
        // Identity links are intentionally retained: historical evidence can still depend on them.
    }
}
