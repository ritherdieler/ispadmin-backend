package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.Subscription

interface MikrotikRepository {

    fun reactivateSubscriptionService(subscription: Subscription)

}
