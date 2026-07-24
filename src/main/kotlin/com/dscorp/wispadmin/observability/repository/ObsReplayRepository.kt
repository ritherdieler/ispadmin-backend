package com.dscorp.wispadmin.observability.repository

import com.dscorp.wispadmin.observability.entity.ObsReplay
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface ObsReplayRepository : JpaRepository<ObsReplay, Long> {

    fun findByCreatedAtBefore(threshold: LocalDateTime): List<ObsReplay>

    fun findBySessionIdOrderByCreatedAtAsc(sessionId: String): List<ObsReplay>

    @Query("SELECT DISTINCT r.sessionId FROM ObsReplay r WHERE r.sessionId IN :sessionIds")
    fun findSessionIdsWithReplay(@Param("sessionIds") sessionIds: List<String>): List<String>
}
