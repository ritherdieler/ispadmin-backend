package com.dscorp.wispadmin.wispadmin.requestbody

data class PasswordAttendanceBody(
    val username: String = "",
    val password: String = "",
    val action: VerifyFaceBody.Action? = null
)
