package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import me.legrange.mikrotik.ApiConnection


interface MikrotikRepository {

//    fun cutSubscriptionService()
//
//    fun getNetworkSegments(): List<Map<String?, String?>>
//
//    fun registerSubscription(subscription: SubscriptionRequest)
//
    fun reactivateSubscriptionService(subscription: Subscription)
//
//    fun updateSubscriptionPlan(subscription: SubscriptionRequest)
//    fun registerIpPool(ipSegment: String)


}


