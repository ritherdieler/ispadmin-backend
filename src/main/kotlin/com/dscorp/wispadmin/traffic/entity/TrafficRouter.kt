package com.dscorp.wispadmin.traffic.entity

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "traffic_router")
data class TrafficRouter(
    @Id
    var id: Int = 0,
    @Column(nullable = false, length = 120)
    var name: String = "",
    @Column(nullable = false, length = 64)
    var host: String = "",
    @Column(nullable = false, length = 80)
    var username: String = "",
    @Column(nullable = false, length = 120)
    var password: String = "",
    @Column(nullable = false)
    var enabled: Boolean = true,
)
