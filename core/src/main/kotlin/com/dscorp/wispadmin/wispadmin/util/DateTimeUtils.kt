package com.dscorp.wispadmin.wispadmin.util

import java.time.Instant
import java.time.LocalDateTime
import com.dscorp.wispadmin.wispadmin.util.AppTimeZone

fun Long?.toLocalDateTimeOrNull(): LocalDateTime? {
    if (this == null || this == 0L) return null

    return LocalDateTime.ofInstant(
        Instant.ofEpochMilli(this),
        AppTimeZone.zoneId()
    )
}