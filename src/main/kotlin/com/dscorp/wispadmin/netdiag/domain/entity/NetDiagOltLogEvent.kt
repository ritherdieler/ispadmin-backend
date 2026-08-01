package com.dscorp.wispadmin.netdiag.domain.entity

import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "net_diag_olt_log_event")
class NetDiagOltLogEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "received_at", nullable = false)
    var receivedAt: Instant = Instant.now(),

    @Column(name = "source_ip", length = 64)
    var sourceIp: String? = null,

    @Column(name = "raw_message", nullable = false, columnDefinition = "MEDIUMTEXT")
    var rawMessage: String = "",

    @Column(name = "reason_code", length = 64)
    var reasonCode: String? = null,

    @Column
    var board: Int? = null,

    @Column
    var port: Int? = null,

    @Column(name = "onu_index")
    var onuIndex: Int? = null,

    @Column(name = "target_id")
    var targetId: Long? = null,

    @Column(length = 8)
    var severity: String? = null,

    @Column(name = "incident_id")
    var incidentId: Long? = null,

    @Column(nullable = false, length = 32)
    var channel: String = "cli_alarm_active",

    @Column(name = "alarm_id_hex", length = 32)
    var alarmIdHex: String? = null,

    @Column(name = "alarm_name", length = 256)
    var alarmName: String? = null,

    @Column(length = 64)
    var component: String? = null,

    @Column(name = "is_clear", nullable = false)
    var isClear: Boolean = false,

    @Column(name = "is_unparsed", nullable = false)
    var isUnparsed: Boolean = false
)
