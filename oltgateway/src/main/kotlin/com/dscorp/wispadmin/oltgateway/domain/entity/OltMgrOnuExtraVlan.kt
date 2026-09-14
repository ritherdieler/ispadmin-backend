package com.dscorp.wispadmin.oltgateway.domain.entity

import java.io.Serializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

data class OltMgrOnuExtraVlanId(
    var onu: Long = 0,
    var vlanId: Int = 0
) : Serializable

@Entity
@Table(name = "olt_mgr_onu_extra_vlan")
@IdClass(OltMgrOnuExtraVlanId::class)
class OltMgrOnuExtraVlan(
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "onu_id")
    var onu: OltMgrOnu,

    @Id
    @Column(name = "vlan_id")
    var vlanId: Int = 0
)
