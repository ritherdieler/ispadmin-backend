package com.dscorp.wispadmin.observability.repository

import com.dscorp.wispadmin.observability.entity.ObsSystemMetric
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface ObsSystemMetricRepository : JpaRepository<ObsSystemMetric, Long> {

    fun findByBucketStartBetweenOrderByBucketStartAsc(
        from: LocalDateTime,
        to: LocalDateTime
    ): List<ObsSystemMetric>

    fun findFirstByOrderBySampledAtDesc(): ObsSystemMetric?

    @Modifying
    @Query("DELETE FROM ObsSystemMetric m WHERE m.bucketStart < :threshold")
    fun deleteOlderThan(@Param("threshold") threshold: LocalDateTime): Int
}
