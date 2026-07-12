package com.dscorp.wispadmin.observability.repository

import com.dscorp.wispadmin.observability.entity.ObsAlertEvent
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface ObsAlertEventRepository : JpaRepository<ObsAlertEvent, Long> {

    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<ObsAlertEvent>

    @Query("SELECT COUNT(e) FROM ObsAlertEvent e WHERE e.dedupKey = :dedupKey AND e.createdAt >= :since")
    fun countRecentByDedupKey(
        @Param("dedupKey") dedupKey: String,
        @Param("since") since: LocalDateTime
    ): Long

    @Modifying
    @Query("DELETE FROM ObsAlertEvent e WHERE e.createdAt < :threshold")
    fun deleteOlderThan(@Param("threshold") threshold: LocalDateTime): Int
}
