package com.dscorp.wispadmin.observability.entity

import javax.persistence.*

@Entity
@Table(name = "obs_delivery_receipt")
class ObsDeliveryReceipt(
    @Id @Column(length = 64) var id: String = "",
    @Column(name = "payload_hash", nullable = false, length = 64) var payloadHash: String = "",
    @Column(name = "issue_id") var issueId: Long? = null,
    @Version var version: Long? = null,
)
