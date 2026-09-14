package com.dscorp.wispadmin.wispadmin.data.model

import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table
import javax.persistence.UniqueConstraint

@Entity
@Table(
    name = "crm_integration_setting",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_crm_integration_setting_key", columnNames = ["setting_key"])
    ]
)
data class CrmIntegrationSetting(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "setting_key", nullable = false, length = 128)
    var settingKey: String = "",

    @Column(name = "value_ciphertext", length = 4000)
    var valueCiphertext: String? = null,

    @Column(name = "value_plain", length = 1000)
    var valuePlain: String? = null,

    @Column(name = "sensitive_flag", nullable = false)
    var sensitive: Boolean = false,

    @Column(name = "updated_by", length = 128)
    var updatedBy: String? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
