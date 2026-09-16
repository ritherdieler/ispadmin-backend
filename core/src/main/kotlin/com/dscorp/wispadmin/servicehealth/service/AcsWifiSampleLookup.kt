package com.dscorp.wispadmin.servicehealth.service

object AcsWifiSampleLookup {
    fun idAfterUpsert(byObservedSql: Long?, byObservedEntity: Long?, latest: Long?): Long =
        byObservedSql ?: byObservedEntity ?: latest ?: throw IllegalStateException("ACS_SAMPLE_ID_NOT_FOUND")
}
