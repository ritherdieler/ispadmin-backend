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
    name = "whatsapp_marketing_optout",
    indexes = [
        Index(name = "uk_wa_marketing_optout_phone", columnList = "phone", unique = true)
    ]
)
data class WhatsAppMarketingOptOut(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    @Column(length = 20, nullable = false)
    var phone: String,

    @Column(length = 64)
    var category: String? = null,

    @Column(length = 16, nullable = false)
    var status: String = OPTED_OUT,

    var updatedAt: LocalDateTime = LocalDateTime.now(),

    var createdAt: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        const val OPTED_OUT = "OPTED_OUT"
        const val RESUMED = "RESUMED"
    }
}
