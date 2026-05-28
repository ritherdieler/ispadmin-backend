package com.dscorp.wispadmin.wispadmin.data.model

import com.dscorp.wispadmin.wispadmin.controller.toDto
import com.google.gson.Gson
import java.util.*
import javax.persistence.*

@Entity
@Table(
    indexes = [
        // Índices para optimizar consultas del dashboard
        Index(name = "idx_subscription_log_date_action_type", columnList = "date, actionType"),
        Index(name = "idx_subscription_log_action_type_date", columnList = "actionType, date"),
        Index(name = "idx_subscription_log_subscription_date", columnList = "subscription_id, date")
    ]
)
data class SubscriptionLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,

    @ManyToOne
    @JoinColumn(name = "subscription_id")
    val subscription: Subscription,

    val date: Date = Date(),

    @Enumerated(EnumType.STRING)
    val actionType: SubscriptionActionType,

    val planName:String? = null,
    val planPrince: Double? = null,
    val planId : Int? = null,

//    @Lob
//    @Column(length = 3000)
//    val currentPlan: String = Gson().toJson(subscription.plan!!.toDto()),
)

