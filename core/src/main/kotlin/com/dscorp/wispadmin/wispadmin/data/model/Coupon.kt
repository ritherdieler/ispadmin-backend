package com.dscorp.wispadmin.wispadmin.data.model

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id

@Entity

data class Coupon(
    @GeneratedValue
    @Id
    var id: Int? = null,
    @Column(unique = true)
    var code: String? = null,
    var description: String? = null,
    var discount: Int? = null,
    var expirationDate: Long? = null,
    var isUsed: Boolean = false,
)