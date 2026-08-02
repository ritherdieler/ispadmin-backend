package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.CrmChannel
import com.dscorp.wispadmin.wispadmin.data.model.CrmConversation
import com.dscorp.wispadmin.wispadmin.data.model.CrmConversationStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface CrmConversationRepository : JpaRepository<CrmConversation, Long> {

    fun findByPhoneAndChannel(phone: String, channel: CrmChannel): CrmConversation?

    fun findBySubscriptionIdOrderByLastInboundAtDesc(subscriptionId: Int): List<CrmConversation>

    fun findByStatusInOrderByLastInboundAtDesc(statuses: Collection<CrmConversationStatus>): List<CrmConversation>

    fun findByAssignedAgentIdAndStatusOrderByLastInboundAtDesc(
        assignedAgentId: Int,
        status: CrmConversationStatus
    ): List<CrmConversation>

    fun findByResolvedAtBetween(from: LocalDateTime, to: LocalDateTime): List<CrmConversation>

    fun findByStatusIn(statuses: Collection<CrmConversationStatus>): List<CrmConversation>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE CrmConversation c
        SET c.assignedAgentId = :agentId,
            c.status = com.dscorp.wispadmin.wispadmin.data.model.CrmConversationStatus.ASSIGNED,
            c.claimedAt = :claimedAt,
            c.updatedAt = :claimedAt,
            c.version = c.version + 1
        WHERE c.id = :id
          AND c.assignedAgentId IS NULL
          AND c.status IN (
            com.dscorp.wispadmin.wispadmin.data.model.CrmConversationStatus.NEW,
            com.dscorp.wispadmin.wispadmin.data.model.CrmConversationStatus.PENDING,
            com.dscorp.wispadmin.wispadmin.data.model.CrmConversationStatus.REOPENED
          )
        """
    )
    fun claimIfUnassigned(
        @Param("id") id: Long,
        @Param("agentId") agentId: Int,
        @Param("claimedAt") claimedAt: LocalDateTime
    ): Int
}
