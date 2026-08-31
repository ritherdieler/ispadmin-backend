package com.dscorp.wispadmin.servicehealth.domain

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object UtcInstantText {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC)

    fun format(instant: Instant): String = formatter.format(instant)
}
