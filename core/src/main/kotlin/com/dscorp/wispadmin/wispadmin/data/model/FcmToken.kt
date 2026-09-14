package com.dscorp.wispadmin.wispadmin.data.model

import org.hibernate.annotations.DynamicUpdate
import javax.persistence.Entity
import javax.persistence.Id

@Entity
@DynamicUpdate
data class FcmToken(
    @Id
    var subscriptionId:Int,
    var token: String
)