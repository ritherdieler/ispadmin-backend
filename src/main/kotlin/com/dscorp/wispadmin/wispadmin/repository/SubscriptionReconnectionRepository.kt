package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.SubscriptionReconnection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SubscriptionReconnectionRepository : JpaRepository<SubscriptionReconnection, Int> {
    fun findBySubscriptionOrderByReconnectionDateDesc(subscription: Subscription): List<SubscriptionReconnection>
}
