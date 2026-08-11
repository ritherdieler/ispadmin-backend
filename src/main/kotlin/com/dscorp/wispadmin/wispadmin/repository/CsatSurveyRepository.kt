package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.CsatSurvey
import com.dscorp.wispadmin.wispadmin.data.model.CsatSurveyStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface CsatSurveyRepository : JpaRepository<CsatSurvey, Long> {

    fun findByTicketId(ticketId: Int): CsatSurvey?

    fun findByCaptureIdempotencyKey(captureIdempotencyKey: String): CsatSurvey?

    fun findByPhoneAndStatusIn(phone: String, statuses: Collection<CsatSurveyStatus>): List<CsatSurvey>

    @Query(
        """
        SELECT s FROM CsatSurvey s
        WHERE s.status IN :statuses
          AND s.nextAttemptAt IS NOT NULL
          AND s.nextAttemptAt <= :now
        ORDER BY s.nextAttemptAt ASC
        """
    )
    fun findDueForSend(
        @Param("statuses") statuses: Collection<CsatSurveyStatus>,
        @Param("now") now: LocalDateTime
    ): List<CsatSurvey>

    @Query(
        """
        SELECT s FROM CsatSurvey s
        WHERE s.status IN :statuses
          AND s.expiresAt <= :now
        ORDER BY s.expiresAt ASC
        """
    )
    fun findExpiredCandidates(
        @Param("statuses") statuses: Collection<CsatSurveyStatus>,
        @Param("now") now: LocalDateTime
    ): List<CsatSurvey>

    @Query(
        """
        SELECT s FROM CsatSurvey s
        WHERE s.scheduledAt >= :from
          AND s.scheduledAt < :to
        ORDER BY s.scheduledAt DESC
        """
    )
    fun findByScheduledAtBetween(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime
    ): List<CsatSurvey>

    fun findByStatus(status: CsatSurveyStatus): List<CsatSurvey>

    @Query(
        """
        SELECT s FROM CsatSurvey s
        WHERE s.status = :status
          AND s.commentWindowExpiresAt IS NOT NULL
          AND s.commentWindowExpiresAt <= :now
        ORDER BY s.commentWindowExpiresAt ASC
        """
    )
    fun findCommentWindowExpired(
        @Param("status") status: CsatSurveyStatus,
        @Param("now") now: LocalDateTime
    ): List<CsatSurvey>
}
