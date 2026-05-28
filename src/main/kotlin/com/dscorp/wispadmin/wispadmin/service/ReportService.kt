package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class ReportService {

    @Autowired
    lateinit var subscriptionRepository: SubscriptionRepository

    fun getCancelledSubscriptionsBetweenTwoDates(
        firstDayOfMonthInMillis: Long,
        lastDayOfMonthInMillis: Long
    ): List<Subscription> {
        return subscriptionRepository.findSubscriptionsByCancellationDate(
            firstDayOfMonthInMillis,
            lastDayOfMonthInMillis
        )
    }

}