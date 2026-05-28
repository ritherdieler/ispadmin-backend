package com.dscorp.wispadmin.wispadmin.response

data class VerifyFaceResponse(
    val matched: Boolean,
    val userId: Int? = null,
    val userName: String? = null,
    val userDni: String? = null,
    val userType: String? = null,
    val action: String? = null,
    val message: String? = null,
    val checkInTime: String? = null,
    val attendanceStatus: String? = null,
    val nextAction: String? = null,
    val alreadyRegistered: Boolean = false,
    val requiresReenrollment: Boolean = false
)
