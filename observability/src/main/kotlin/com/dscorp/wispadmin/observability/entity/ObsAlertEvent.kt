package com.dscorp.wispadmin.observability.entity

import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Lob
import javax.persistence.Table

@Entity
@Table(
    name = "obs_alert_event",
    indexes = [
        Index(name = "idx_obs_alert_event_rule", columnList = "rule_id"),
        Index(name = "idx_obs_alert_event_created", columnList = "created_at"),
        Index(name = "idx_obs_alert_event_dedup", columnList = "dedup_key, created_at")
    ]
)
data class ObsAlertEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "rule_id")
    var ruleId: Long? = null,

    @Column(name = "rule_name", length = 160)
    var ruleName: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 40)
    var type: ObsAlertType = ObsAlertType.NEW_ISSUE,

    @Column(name = "title", length = 500)
    var title: String? = null,

    @Lob
    @Column(name = "message", columnDefinition = "TEXT")
    var message: String? = null,

    @Column(name = "severity", length = 30)
    var severity: String? = null,

    @Column(name = "issue_id")
    var issueId: Long? = null,

    @Column(name = "observed_value")
    var observedValue: Double? = null,

    @Column(name = "threshold_value")
    var thresholdValue: Double? = null,

    @Column(name = "dedup_key", length = 200)
    var dedupKey: String? = null,

    @Column(name = "delivered")
    var delivered: Boolean = false,

    @Column(name = "delivery_detail", length = 1000)
    var deliveryDetail: String? = null,

    @Column(name = "created_at")
    var createdAt: LocalDateTime? = null
)
