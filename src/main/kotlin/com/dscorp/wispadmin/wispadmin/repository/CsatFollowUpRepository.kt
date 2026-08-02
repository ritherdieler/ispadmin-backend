package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.CsatFollowUp
import com.dscorp.wispadmin.wispadmin.data.model.CsatFollowUpStatus
import org.springframework.data.jpa.repository.JpaRepository

interface CsatFollowUpRepository : JpaRepository<CsatFollowUp, Long> {
    fun findBySurveyId(surveyId: Long): CsatFollowUp?
    fun findByStatusInOrderByCreatedAtDesc(statuses: Collection<CsatFollowUpStatus>): List<CsatFollowUp>
}
