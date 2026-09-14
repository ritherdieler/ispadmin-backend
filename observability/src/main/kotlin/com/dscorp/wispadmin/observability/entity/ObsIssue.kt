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

enum class ObsIssueStatus {
    OPEN,
    RESOLVED,
    IGNORED,
    MUTED
}

@Entity
@Table(
    name = "obs_issue",
    indexes = [
        Index(name = "idx_obs_issue_fingerprint", columnList = "fingerprint", unique = true),
        Index(name = "idx_obs_issue_platform", columnList = "platform"),
        Index(name = "idx_obs_issue_severity", columnList = "severity"),
        Index(name = "idx_obs_issue_status", columnList = "status"),
        Index(name = "idx_obs_issue_last_seen", columnList = "last_seen"),
        Index(name = "idx_obs_issue_tracker_key", columnList = "tracker_issue_key")
    ]
)
data class ObsIssue(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "fingerprint", nullable = false, unique = true, length = 64)
    var fingerprint: String = "",

    @Column(name = "title", length = 500)
    var title: String? = null,

    @Column(name = "platform", length = 60)
    var platform: String? = null,

    @Column(name = "severity", length = 30)
    var severity: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    var status: ObsIssueStatus = ObsIssueStatus.OPEN,

    @Column(name = "error_type", length = 300)
    var errorType: String? = null,

    @Lob
    @Column(name = "last_message", columnDefinition = "TEXT")
    var lastMessage: String? = null,

    @Column(name = "last_environment", length = 60)
    var lastEnvironment: String? = null,

    @Column(name = "last_release", length = 120)
    var lastRelease: String? = null,

    @Column(name = "event_count")
    var eventCount: Long = 0,

    @Column(name = "first_seen")
    var firstSeen: LocalDateTime? = null,

    @Column(name = "last_seen")
    var lastSeen: LocalDateTime? = null,

    @Column(name = "jira_issue_key", length = 60)
    var jiraIssueKey: String? = null,

    @Column(name = "tracker_provider", length = 30)
    var trackerProvider: String? = null,

    @Column(name = "tracker_issue_key", length = 100)
    var trackerIssueKey: String? = null,

    @Column(name = "tracker_browse_url", length = 500)
    var trackerBrowseUrl: String? = null
)
