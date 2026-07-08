package com.dscorp.wispadmin.wispadmin.data.model

import java.util.Date
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id

@Entity
data class CommercialOpportunity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,
    val zoneName: String,
    @Enumerated(EnumType.STRING)
    val priority: OpportunityPriority = OpportunityPriority.MEDIUM,
    val reason: String? = null,
    val estimatedClients: Int = 0,
    @Enumerated(EnumType.STRING)
    val evaluationStatus: OpportunityEvaluationStatus = OpportunityEvaluationStatus.PENDING,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val notes: String? = null,
    val createdAt: Date = Date(),
)

enum class OpportunityPriority {
    LOW,
    MEDIUM,
    HIGH,
}

enum class OpportunityEvaluationStatus {
    PENDING,
    APPROVED,
    REJECTED,
    IN_PROGRESS,
}
