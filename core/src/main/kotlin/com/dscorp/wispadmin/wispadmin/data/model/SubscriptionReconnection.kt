package com.dscorp.wispadmin.wispadmin.data.model

import java.time.LocalDateTime
import javax.persistence.*

@Entity
@Table(name = "subscription_reconnection")
data class SubscriptionReconnection(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "subscription_id")
    var subscription: Subscription? = null,
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "responsible_user_id")
    var responsible: User? = null,
    
    var reconnectionDate: LocalDateTime? = null,
    
    var notes: String? = null
)
