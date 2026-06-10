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
        val firstDayOfMonth = java.time.Instant.ofEpochMilli(firstDayOfMonthInMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()

        val lastDayOfMonth = java.time.Instant.ofEpochMilli(lastDayOfMonthInMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()

        return subscriptionRepository.findSubscriptionsByCancellationDate(
            firstDayOfMonth,
            lastDayOfMonth
        )
    }

}