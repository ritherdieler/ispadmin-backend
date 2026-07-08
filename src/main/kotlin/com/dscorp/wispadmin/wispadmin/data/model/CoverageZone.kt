package com.dscorp.wispadmin.wispadmin.data.model

import java.util.Date
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id

@Entity
data class CoverageZone(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,
    val name: String,
    @Enumerated(EnumType.STRING)
    val coverageType: CoverageType = CoverageType.PARTIAL,
    @Enumerated(EnumType.STRING)
    val status: CoverageStatus = CoverageStatus.ACTIVE,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val geometryGeoJson: String? = null,
    val notes: String? = null,
    val createdAt: Date = Date(),
)

enum class CoverageType {
    FULL,
    PARTIAL,
    NONE,
}

enum class CoverageStatus {
    ACTIVE,
    PLANNED,
    INACTIVE,
}
