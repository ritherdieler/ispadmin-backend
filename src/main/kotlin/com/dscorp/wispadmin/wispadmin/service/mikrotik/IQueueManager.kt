package com.dscorp.wispadmin.wispadmin.service.mikrotik

import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import me.legrange.mikrotik.ApiConnection
import java.util.concurrent.CompletableFuture

interface IQueueManager {
    
    fun buildQueueName(subscription: Subscription): String
    
    fun recreateQueueForSubscription(connection: ApiConnection, subscription: Subscription): Boolean
    
    fun configureMikroTikQueue(connection: ApiConnection, subscription: Subscription)
    
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



