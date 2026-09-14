package com.dscorp.wispadmin.wispadmin.data.model

import java.time.LocalDateTime
import javax.persistence.*

@Entity
@Table(name = "whatsapp_phone_session")
data class WhatsAppPhoneSession(
    @Id
    @Column(length = 20)
    var phone: String = "",

    var serviceWindowExpiresAt: LocalDateTime? = null,

    var updatedAt: LocalDateTime = LocalDateTime.now()
)
