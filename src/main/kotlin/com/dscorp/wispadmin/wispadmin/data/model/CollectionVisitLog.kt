package com.dscorp.wispadmin.wispadmin.data.model

import java.time.LocalDateTime
import javax.persistence.*

@Entity
@Table(name = "collection_visit_log")
data class CollectionVisitLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    @Column(name = "subscription_id")
    var subscriptionId: Int? = null,

    @Column(name = "client_id")
    var clientId: Int,

    @Column(name = "route_type", length = 20)
    var routeType: String = "sector",

    @Column(name = "zone_name", length = 200)
    var zoneName: String? = null,

    @Column(name = "collector_user_id")
    var collectorUserId: Int? = null,

    @Column(length = 30)
    var status: String = "visited",

    @Column(length = 500)
    var comment: String? = null,

    var latitude: Double? = null,

    var longitude: Double? = null,

    @Column(name = "visited_at")
    var visitedAt: LocalDateTime = LocalDateTime.now(),
)
