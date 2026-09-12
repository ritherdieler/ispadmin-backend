package com.dscorp.wispadmin.wispadmin.service.subscription

import com.dscorp.wispadmin.wispadmin.data.model.AccessMigrationStage
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionAccessMigrationRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class AccessMigrationQuarantineJob(
    private val migrationRepository: SubscriptionAccessMigrationRepository,
    private val accessMigrationService: AccessMigrationService,
) {
    var clock: Clock = Clock.systemDefaultZone()
    private val logger = LoggerFactory.getLogger(AccessMigrationQuarantineJob::class.java)

    @Scheduled(cron = "0 15 3 * * *", zone = "America/Lima")
    fun finishDueQuarantines(): Int {
        val now = LocalDateTime.ofInstant(Instant.now(clock), ZoneId.of("America/Lima"))
        val due = migrationRepository.findByStageAndQuarantineUntilLessThanEqual(
            AccessMigrationStage.QUARANTINE,
            now,
        )
        due.forEach { row ->
            runCatching { accessMigrationService.finishQuarantine(row) }
                .onFailure { error ->
                    logger.error(
                        "No se pudo cerrar la cuarentena PPPoE de la suscripción {}",
                        row.subscriptionId,
                        error,
                    )
                }
        }
        return due.size
    }
}
