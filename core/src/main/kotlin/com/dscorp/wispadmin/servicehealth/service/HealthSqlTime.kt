package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.servicehealth.domain.UtcInstantText
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit

object HealthSqlTime {
    fun timestamp(value: Instant): Timestamp = Timestamp.from(value.truncatedTo(ChronoUnit.SECONDS))

    fun utcSqlText(value: Instant): String = UtcInstantText.formatSeconds(value)
}

object AcsWifiSampleLookup {
    fun idAfterUpsert(byObservedSql: Long?, byObservedEntity: Long?, latest: Long?): Long =
        byObservedSql ?: byObservedEntity ?: latest ?: throw IllegalStateException("ACS_SAMPLE_ID_NOT_FOUND")
}
