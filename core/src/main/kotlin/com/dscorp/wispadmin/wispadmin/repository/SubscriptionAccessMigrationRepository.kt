package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.AccessMigrationStage
import com.dscorp.wispadmin.wispadmin.data.model.SubscriptionAccessMigration
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface SubscriptionAccessMigrationRepository : JpaRepository<SubscriptionAccessMigration, Long> {
    fun findTopBySubscriptionIdOrderByAttemptDesc(subscriptionId: Int): SubscriptionAccessMigration?

    fun findByStageAndQuarantineUntilLessThanEqual(
        stage: AccessMigrationStage,
        quarantineUntil: LocalDateTime,
    ): List<SubscriptionAccessMigration>
}
