package com.dscorp.wispadmin.oltgateway.domain.entity

import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "olt_lab_onu")
class OltLabOnu(
    @Id
    @Column(nullable = false, length = 64)
    var sn: String = "",

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
