package com.dscorp.wispadmin.oltgateway.domain.entity

import java.math.BigDecimal
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.MapsId
import javax.persistence.OneToOne
import javax.persistence.Table

@Entity
@Table(name = "olt_mgr_onu_status_current")
class OltMgrOnuStatusCurrent(
    @Id
    @Column(name = "onu_id")
    var onuId: Long? = null,

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "onu_id")
    var onu: OltMgrOnu,

    @Column(name = "run_state", nullable = false, length = 12)
    var runState: String = "offline",

    @Column(name = "last_status_change")
    var lastStatusChange: Instant? = null,

    @Column(name = "last_down_cause", length = 32)
    var lastDownCause: String? = null,

    @Column(name = "signal_category", length = 10)
    var signalCategory: String? = null,

    @Column(name = "onu_rx_dbm", precision = 6, scale = 2)
    var onuRxDbm: BigDecimal? = null,

    @Column(name = "olt_rx_dbm", precision = 6, scale = 2)
    var oltRxDbm: BigDecimal? = null,

    @Column(name = "onu_tx_dbm", precision = 6, scale = 2)
    var onuTxDbm: BigDecimal? = null,

    @Column(name = "temperature_c")
    var temperatureC: Int? = null,

    @Column(name = "distance_m")
    var distanceM: Int? = null,

    @Column(name = "match_state", length = 10)
    var matchState: String? = null,

    @Column(name = "polled_at", nullable = false)
    var polledAt: Instant = Instant.now()
)
