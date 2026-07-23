package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.CollectionVisitLog
import com.dscorp.wispadmin.wispadmin.repository.CollectionVisitLogRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.LocalDateTime

class CollectionVisitServiceTest {

    private val collectionVisitLogRepository = mock(CollectionVisitLogRepository::class.java)
    private val subscriptionRepository = mock(SubscriptionRepository::class.java)
    private val service = CollectionVisitService(collectionVisitLogRepository, subscriptionRepository)

    private val since = LocalDateTime.of(2026, 5, 23, 0, 0)

    @Test
    fun `getRecentCommentsForClients groups comments by client within window`() {
        val logs = listOf(
            visitLog(id = 1, clientId = 10, comment = "No estaba", visitedAt = since.plusDays(5)),
            visitLog(id = 2, clientId = 10, comment = "Segunda visita", visitedAt = since.plusDays(10)),
            visitLog(id = 3, clientId = 20, comment = "Cliente pagó parcial", visitedAt = since.plusDays(2)),
        )
        `when`(
            collectionVisitLogRepository.findCommentsByClientIdsSince(
                eq(listOf(10, 20)),
                eq(since),
            ),
        ).thenReturn(logs)

        val result = service.getRecentCommentsForClients(listOf(10, 20), since)

        assertEquals(2, result[10]?.size)
        assertEquals("Segunda visita", result[10]?.first()?.comment)
        assertEquals("No estaba", result[10]?.last()?.comment)
        assertEquals(1, result[20]?.size)
    }

    @Test
    fun `getRecentCommentsForClients returns empty map for empty client ids`() {
        val result = service.getRecentCommentsForClients(emptyList(), since)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getRecentVisits filters by since when provided`() {
        val sinceParam = LocalDateTime.of(2026, 1, 1, 0, 0)
        val logs = listOf(
            visitLog(id = 1, clientId = 10, comment = "Reciente", visitedAt = sinceParam.plusMonths(1)),
        )
        `when`(
            collectionVisitLogRepository.findCommentsByClientIdSince(10, sinceParam),
        ).thenReturn(logs)

        val result = service.getRecentVisits(10, sinceParam)

        assertEquals(1, result.size)
        assertEquals("Reciente", result.first().comment)
    }

    private fun visitLog(
        id: Int,
        clientId: Int,
        comment: String?,
        visitedAt: LocalDateTime,
    ): CollectionVisitLog = CollectionVisitLog(
        id = id,
        clientId = clientId,
        comment = comment,
        visitedAt = visitedAt,
        status = "no_payment",
    )
}
