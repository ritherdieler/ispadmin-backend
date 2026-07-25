package com.dscorp.wispadmin.wispadmin.data.model

import java.time.LocalDateTime
import javax.persistence.*

@Entity
@Table(
    name = "whatsapp_synced_template",
    indexes = [Index(name = "idx_wa_synced_template_name", columnList = "name")]
)
data class WhatsAppSyncedTemplate(
    @Id
    @Column(length = 64)
    var metaTemplateId: String = "",

    @Column(length = 255, nullable = false)
    var name: String = "",

    @Column(length = 64)
    var status: String? = null,

    @Column(length = 64)
    var category: String? = null,

    @Column(length = 32)
    var qualityScore: String? = null,

    @Column(length = 16)
    var language: String? = null,

    var syncedAt: LocalDateTime = LocalDateTime.now()
)
