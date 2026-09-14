package com.dscorp.wispadmin.wispadmin.data.model

import com.dscorp.wispadmin.wispadmin.dto.FixedCostDto
import com.fasterxml.jackson.annotation.JsonIgnore
import java.util.*
import javax.persistence.*

@Entity
data class FixedCost(
    @GeneratedValue
    @Id
    val id: Int? = null,
    val amount: Double,
    val description: String,
    val note: String,
    @Enumerated(EnumType.STRING)
    val type: FixedCostType,
    val date: Date = Date(),
    val enabled: Boolean=true,
    @OneToOne
    val user: User
) {
    fun toDto()=FixedCostDto (
        id = id ?: 0,
        amount = amount, description = description, note = note, type = type, userId = user.id, date = date
    )

}

enum class FixedCostType {
    STAFF_PAYMENT,
    PROVIDER_PAYMENT,
    SYSTEM_INFRASTRUCTURE,
    OFFICE,
    OTHER
}