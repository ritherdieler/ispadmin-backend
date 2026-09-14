package com.dscorp.wispadmin.wispadmin.data.model

import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Table

@Entity
@Table(
    name = "crm_event_log",
    indexes = [
        Index(name = "idx_crm_event_created", columnList = "createdAt")
    ]
)
data class CrmEventLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, length = 64)
    var eventType: String = "",

    @Column(nullable = false, columnDefinition = "TEXT")
    var payload: String = "",

    @Column(nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)
