package com.dscorp.wispadmin.wispadmin.repository

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WhatsAppCandidateSqlTest {

    @Test
    fun oldestUnpaidJoinPicksOnePaymentPerSubscriptionInDatabase() {
        val join = WhatsAppCandidateSql.OLDEST_UNPAID_PAYMENT_PER_SUBSCRIPTION_JOIN
        assertTrue(join.contains("ROW_NUMBER()"))
        assertTrue(join.contains("PARTITION BY subscription_id"))
        assertTrue(join.contains("ORDER BY billing_date_datetime ASC, id ASC"))
        assertTrue(join.contains("WHERE paid = false"))
        assertTrue(join.contains("ranked.rn = 1"))
    }
}
