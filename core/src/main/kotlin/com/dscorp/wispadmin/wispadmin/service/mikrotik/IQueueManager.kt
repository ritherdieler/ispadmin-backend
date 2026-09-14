package com.dscorp.wispadmin.wispadmin.service.mikrotik

import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import java.util.concurrent.CompletableFuture

interface IQueueManager {
    
    fun buildQueueName(subscription: Subscription): String
    
    fun recreateQueueForSubscription(session: MikrotikSession, subscription: Subscription): Boolean
    
    fun configureMikroTikQueue(session: MikrotikSession, subscription: Subscription)
    
    fun updateMikroTikQueue(subscription: Subscription)
    
    fun createSubscriptionsSimpleQueue(): CompletableFuture<QueueCreationStats>
}

data class QueueCreationStats(
    val totalSubscriptions: Int,
    val totalProcessed: Int,
    val queuesGenerated: Int,
    val errorsCount: Int,
    val omittedByTvCable: Int
)
