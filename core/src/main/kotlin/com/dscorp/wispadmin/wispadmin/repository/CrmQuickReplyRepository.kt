package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.CrmQuickReply
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CrmQuickReplyRepository : JpaRepository<CrmQuickReply, Long> {

    @Query(
        """
        SELECT q FROM CrmQuickReply q
        WHERE q.ownerUserId IS NULL OR q.ownerUserId = :userId
        ORDER BY CASE WHEN q.ownerUserId IS NULL THEN 0 ELSE 1 END, q.title ASC
        """
    )
    fun findVisibleForUser(@Param("userId") userId: Int): List<CrmQuickReply>
}
