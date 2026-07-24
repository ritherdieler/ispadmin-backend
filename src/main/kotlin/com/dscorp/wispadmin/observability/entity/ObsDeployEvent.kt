package com.dscorp.wispadmin.observability.entity

import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Lob
import javax.persistence.Table

@Entity
@Table(
    name = "obs_deploy_event",
    indexes = [
        Index(name = "idx_obs_deploy_platform", columnList = "platform"),
        Index(name = "idx_obs_deploy_deployed_at", columnList = "deployed_at")
    ]
)
data class ObsDeployEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "platform", length = 60)
    var platform: String? = null,

    @Column(name = "app_release", length = 120)
    var release: String? = null,

    @Column(name = "semver", length = 60)
    var semver: String? = null,

    @Column(name = "git_sha", length = 40)
    var gitSha: String? = null,

    @Lob
    @Column(name = "notes", columnDefinition = "TEXT")
    var notes: String? = null,

    @Column(name = "deployed_at")
    var deployedAt: LocalDateTime? = null
)
