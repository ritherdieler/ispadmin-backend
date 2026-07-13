package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.User
import java.io.Serializable

/**
 * A DTO for the {@link com.dscorp.wispadmin.wispadmin.data.model.User} entity
 */
data class UserDto(
    val id: Int? = null,
    val name: String? = null,
    val lastName: String? = null,
    val type: User.UserType? = null,
    val username: String? = null,
    val verified: Boolean? = null,
    val email: String? = null,
    val phone: String? = null,
    val dni: String? = null,
    val obsSessionToken: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
) : Serializable