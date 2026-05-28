package com.dscorp.wispadmin.wispadmin.data.model

import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id

@Entity
data class Onu(
    @Id
    val sn: String ="",
    var board: String ="",
    var onu: String ="",
    var onu_type_id: String ="",
    var onu_type_name: String ="",
    var pon_type: String ="",
    var port: String ="",
    var olt_id: String ="",
)