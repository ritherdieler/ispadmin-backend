package com.dscorp.wispadmin.wispadmin.requestbody

data class PasswordAttendanceBody(
    val username: String = "",
    val password: String = "",
    val action: VerifyFaceBody.Action? = null,
    val occurredAtMillis: Long? = null,
    val attendanceStatus: String? = null
)