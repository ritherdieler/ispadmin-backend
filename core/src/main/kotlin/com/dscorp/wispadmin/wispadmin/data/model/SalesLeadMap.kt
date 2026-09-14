package com.dscorp.wispadmin.wispadmin.data.model

import java.util.Date
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id

@Entity
data class SalesLeadMap(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,
    val referenceName: String,
    val phone: String? = null,
    val sector: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val source: String? = null,
    @Enumerated(EnumType.STRING)
    val leadStatus: SalesLeadStatus = SalesLeadStatus.NEW,
    val notes: String? = null,
    val createdAt: Date = Date(),
)

enum class SalesLeadStatus {
    NEW,
    CONTACTED,
    QUOTED,
    WON,
    LOST,
}
