package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.CollectionVisitLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface CollectionVisitLogRepository : JpaRepository<CollectionVisitLog, Int> {

    fun findTop20ByClientIdOrderByVisitedAtDesc(clientId: Int): List<CollectionVisitLog>

    @Query(
        """
        SELECT v FROM CollectionVisitLog v
        WHERE v.clientId IN :clientIds
          AND v.visitedAt >= :since
          AND v.comment IS NOT NULL
          AND TRIM(v.comment) <> ''
        ORDER BY v.visitedAt DESC
        """,
    )
    fun findCommentsByClientIdsSince(
        @Param("clientIds") clientIds: Collection<Int>,
        @Param("since") since: LocalDateTime,
    ): List<CollectionVisitLog>

    @Query(
        """
        SELECT v FROM CollectionVisitLog v
        WHERE v.clientId = :clientId
          AND v.visitedAt >= :since
          AND v.comment IS NOT NULL
          AND TRIM(v.comment) <> ''
        ORDER BY v.visitedAt DESC
        """,
    )
    fun findCommentsByClientIdSince(
        @Param("clientId") clientId: Int,
        @Param("since") since: LocalDateTime,
    ): List<CollectionVisitLog>

    @Query(
        """
        SELECT v FROM CollectionVisitLog v
        WHERE v.clientId IN :clientIds
          AND v.visitedAt >= :since
        ORDER BY v.visitedAt DESC
        """,
    )
    fun findRecentByClientIdsSince(
        @Param("clientIds") clientIds: Collection<Int>,
        @Param("since") since: LocalDateTime,
    ): List<CollectionVisitLog>
}
