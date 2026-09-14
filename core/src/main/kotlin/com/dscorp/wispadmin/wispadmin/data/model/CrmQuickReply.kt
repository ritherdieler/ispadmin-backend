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
    name = "crm_quick_reply",
    indexes = [
        Index(name = "idx_crm_quick_reply_owner", columnList = "ownerUserId"),
        Index(name = "idx_crm_quick_reply_title", columnList = "title")
    ]
)
data class CrmQuickReply(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, length = 120)
    var title: String = "",

    @Column(nullable = false, length = 2000)
    var body: String = "",

    @Column
    var ownerUserId: Int? = null,

    @Column(nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
