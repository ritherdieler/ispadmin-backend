package com.dscorp.wispadmin.wispadmin.data.model

import java.util.*
import javax.persistence.*

@Entity
data class AssistanceTicket(
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    val id: Int = 0,
    val phone: String = "",
    var category: String,
    var description: String,
    @Enumerated(EnumType.STRING)
    var status: AssistanceTicketStatus = AssistanceTicketStatus.PENDING,
    val comments: String? = null,
    var priority: Int = 0,
    var createdAt: Date = Date(),
    var scheduledAt: Date? = null,
    var assignedAt: Date? = null,
    var resolvedAt: Date? = null,
    var closedAt: Date? = null,
    var externalCustomerName: String? = null,
    var isExternalCustomer: Boolean = false,
    var sheetImageUrl: String? = null,
    var placeName: String? = null,

    @ManyToOne
    @JoinColumn(name = "subscription_id")
    val subscription: Subscription? = null,

    @OneToOne
    @JoinColumn(name = "responsible_id")
    var responsible: User? = null

)

enum class AssistanceTicketStatus(val status: String) {
    PENDING("Por Atender"),
    ASSIGNED("Asignado"),
    IN_PROGRESS("En Progreso"),
    RESOLVED("Resuelto"),
    REOPEN("Reabierto"),
    CLOSED("Cerrado"),
    CANCELLED("Cancelado")
}