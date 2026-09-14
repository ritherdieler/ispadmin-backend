package com.dscorp.wispadmin.wispadmin.data.model

import com.dscorp.wispadmin.wispadmin.dto.MonthlySubscriptionResumeDto
import java.util.*
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id

@Entity
data class MonthlySubscriptionResume(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int,
    val totalActiveSubscriptions: Int,
    val newSubscriptions: Int,
    val cancelledSubscriptions: Int,
    val date: Date
)

fun MonthlySubscriptionResume.toDto() = MonthlySubscriptionResumeDto(
    totalActiveSubscriptions = totalActiveSubscriptions,
    newSubscriptions = newSubscriptions,
    cancelledSubscriptions = cancelledSubscriptions,
    date = date
)

