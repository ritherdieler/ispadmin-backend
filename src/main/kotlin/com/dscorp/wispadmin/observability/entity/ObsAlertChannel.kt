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

enum class ObsAlertChannelType {
    SLACK,
    TELEGRAM,
    WEBHOOK
}

@Entity
@Table(
    name = "obs_alert_channel",
    indexes = [
        Index(name = "idx_obs_alert_channel_enabled", columnList = "enabled"),
        Index(name = "idx_obs_alert_channel_type", columnList = "type")
    ]
)
data class ObsAlertChannel(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "name", nullable = false, length = 160)
    var name: String = "",

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    var type: ObsAlertChannelType = ObsAlertChannelType.WEBHOOK,

    @Column(name = "enabled")
    var enabled: Boolean = true,

    @Column(name = "target", length = 1000)
    var target: String = "",

    @Lob
    @Column(name = "config_json", columnDefinition = "TEXT")
    var configJson: String? = null,

    @Column(name = "created_at")
    var createdAt: LocalDateTime? = null,

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null
)
