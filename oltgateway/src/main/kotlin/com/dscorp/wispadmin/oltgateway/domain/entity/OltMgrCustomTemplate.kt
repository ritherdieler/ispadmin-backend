package com.dscorp.wispadmin.oltgateway.domain.entity

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "olt_mgr_custom_template")
class OltMgrCustomTemplate(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true, length = 64)
    var name: String = "",

    @Column(columnDefinition = "TEXT")
    var content: String? = null
)
