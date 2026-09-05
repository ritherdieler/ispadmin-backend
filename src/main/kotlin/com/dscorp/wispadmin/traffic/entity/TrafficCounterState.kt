package com.dscorp.wispadmin.traffic.entity

import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "traffic_counter_state")
data class TrafficCounterState(
    @Id
    @Column(name = "client_ip", length = 45)
    var clientIp: String = "",
    var subscriptionId: Int? = null,
    var hostDeviceId: Int = 0,
    var lastRxBytes: Long = 0,
    var lastTxBytes: Long = 0,
    var lastRouterUptimeSeconds: Long? = null,
    var lastPolledAt: LocalDateTime? = null,
)
