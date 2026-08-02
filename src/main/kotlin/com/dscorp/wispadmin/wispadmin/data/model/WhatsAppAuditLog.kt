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
    name = "whatsapp_audit_log",
    indexes = [
        Index(name = "idx_wa_audit_created", columnList = "createdAt"),
        Index(name = "idx_wa_audit_action", columnList = "action"),
        Index(name = "idx_wa_audit_operator", columnList = "operatorUsername")
    ]
)
data class WhatsAppAuditLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, length = 64)
    var action: String = "",

    @Column(length = 128)
    var operatorUsername: String? = null,

    @Column(length = 32)
    var phone: String? = null,

    @Column(length = 255)
    var resource: String? = null,

    @Column(length = 1000)
    var details: String? = null,

    @Column(nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)
