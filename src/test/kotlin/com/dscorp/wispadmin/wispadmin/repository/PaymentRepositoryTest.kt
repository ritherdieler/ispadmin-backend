package com.dscorp.wispadmin.wispadmin.repository

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.data.jpa.repository.Query

class PaymentRepositoryTest {

    @Test
    fun reminderCandidateRowsQuerySelectsAggregatedUnpaidTotalsPerSubscription() {
        val query = nativeQueryOn("findReminderCandidatePaymentRows")

        assertTrue(query.contains("total_amount"))
        assertTrue(query.contains("invoice_count"))
        assertTrue(query.contains("period_from"))
        assertTrue(query.contains("period_to"))
        assertTrue(query.contains("s.is_bimonthly"))
        assertTrue(query.contains("GROUP BY"))
        assertTrue(query.contains("SUM("))
        assertFalse(query.contains("ranked.rn = 1"))
        assertFalse(query.contains("ROW_NUMBER()"))
    }

    private fun nativeQueryOn(methodName: String): String {
        val method = PaymentRepository::class.java.methods.first { it.name == methodName }
        val query = method.getAnnotation(Query::class.java)
            ?: error("Missing @Query on $methodName")
        return query.value
    }
}
