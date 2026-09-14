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
    name = "crm_internal_note",
    indexes = [
        Index(name = "idx_crm_note_conversation", columnList = "conversationId"),
        Index(name = "idx_crm_note_created", columnList = "createdAt")
    ]
)
data class CrmInternalNote(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var conversationId: Long = 0,

    @Column(nullable = false)
    var authorId: Int = 0,

    @Column(nullable = false, length = 2000)
    var text: String = "",

    @Column(nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)
