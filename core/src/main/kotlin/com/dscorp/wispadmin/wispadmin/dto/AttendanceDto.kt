package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.User
import java.util.Date
import javax.persistence.OneToOne

data class AttendanceDto (
    val id:Int,
    val checkIn: Date,
    val checkOut: Date? = null,
    val method: String,
    val status: String,
    val userId: Int
)