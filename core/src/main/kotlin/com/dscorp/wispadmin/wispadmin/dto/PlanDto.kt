package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import java.io.Serializable

/**
 * A DTO for the {@link com.dscorp.wispadmin.wispadmin.data.model.Plan} entity
 */
data class PlanDto(
    val id: Int? = null,
    val name: String? = null,
    val price: Double? = null,
    val downloadSpeed: Int? = null,
    val uploadSpeed: Int? = null,
    val type: InstallationType? = null,
    val isActive: Boolean = true,
    val activeSubscriptionsCount: Int = 0
) : Serializable