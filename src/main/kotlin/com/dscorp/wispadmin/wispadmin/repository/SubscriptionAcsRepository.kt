package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.SubscriptionAcs
import org.springframework.data.jpa.repository.JpaRepository

interface SubscriptionAcsRepository : JpaRepository<SubscriptionAcs, Int> {
    fun findByGenieacsDeviceId(deviceId: String): List<SubscriptionAcs>
}
