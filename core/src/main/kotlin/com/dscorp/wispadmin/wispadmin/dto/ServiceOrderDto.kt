package com.dscorp.wispadmin.wispadmin.dto

import java.io.Serializable

/**
 * A DTO for the {@link com.dscorp.wispadmin.wispadmin.data.model.ServiceOrder} entity
 */
data class ServiceOrderDto(
    val id: Int? = null,
    val issue: String? = null,
    val createDate: Long? = null,
    val attentionDate: Long? = null,
    val additionalDetails: String? = null,
    val createdBy: UserDto
) : Serializable