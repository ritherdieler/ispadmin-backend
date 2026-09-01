package com.dscorp.wispadmin.servicehealth.service

import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit

object HealthSqlTime {
    fun timestamp(value: Instant): Timestamp = Timestamp.from(value.truncatedTo(ChronoUnit.SECONDS))
}

object AcsWifiSampleLookup {
    fun idAfterUpsert(byObservedSql: Long?, byObservedEntity: Long?, latest: Long?): Long =
        byObservedSql ?: byObservedEntity ?: latest ?: throw IllegalStateException("ACS_SAMPLE_ID_NOT_FOUND")
}
