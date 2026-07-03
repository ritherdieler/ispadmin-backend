package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ReportService {

    @Autowired
    lateinit var subscriptionRepository: SubscriptionRepository

    fun getCancelledSubscriptionsBetweenTwoDates(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<Subscription> {
        return subscriptionRepository.findSubscriptionsByCancellationDate(
            startDate,
            endDate
        )
    }

}