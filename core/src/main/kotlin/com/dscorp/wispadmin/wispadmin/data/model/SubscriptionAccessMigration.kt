package com.dscorp.wispadmin.wispadmin.data.model

import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Table

@Entity
@Table(
    name = "subscription_access_migration",
    indexes = [
        Index(name = "idx_access_migration_subscription", columnList = "subscription_id"),
        Index(name = "idx_access_migration_stage", columnList = "stage"),
        Index(name = "idx_access_migration_quarantine", columnList = "stage, quarantine_until"),
    ]
)
data class SubscriptionAccessMigration(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "subscription_id", nullable = false)
    var subscriptionId: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", length = 32, nullable = false)
    var stage: AccessMigrationStage = AccessMigrationStage.ELIGIBLE,

    @Column(name = "attempt", nullable = false)
    var attempt: Int = 1,

    @Column(name = "previous_ip", length = 45)
    var previousIp: String? = null,

    @Column(name = "previous_vlan", length = 10)
    var previousVlan: String? = null,

    @Column(name = "previous_queue_target", length = 45)
    var previousQueueTarget: String? = null,

    @Column(name = "pppoe_username", length = 64)
    var pppoeUsername: String? = null,

    @Column(name = "quarantine_until")
    var quarantineUntil: LocalDateTime? = null,

    @Column(name = "failure_reason", length = 500)
    var failureReason: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)
