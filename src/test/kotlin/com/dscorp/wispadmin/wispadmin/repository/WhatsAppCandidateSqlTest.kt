package com.dscorp.wispadmin.wispadmin.repository

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WhatsAppCandidateSqlTest {

    @Test
    fun unpaidAggregateJoinGroupsAllUnpaidInvoicesPerSubscription() {
        val join = WhatsAppCandidateSql.OLDEST_UNPAID_PAYMENT_PER_SUBSCRIPTION_JOIN

        assertTrue(join.contains("GROUP BY"))
        assertTrue(join.contains("subscription_id"))
        assertTrue(join.contains("SUM("))
        assertTrue(join.contains("COUNT("))
        assertTrue(join.contains("MIN("))
        assertTrue(join.contains("MAX("))
        assertTrue(join.contains("paid = false") || join.contains("paid=false"))
        assertFalse(join.contains("ROW_NUMBER()"))
        assertFalse(join.contains("ranked.rn = 1"))
    }

    @Test
    fun unpaidAggregateJoinKeepsOldestPaymentIdAsSendAnchor() {
        val join = WhatsAppCandidateSql.OLDEST_UNPAID_PAYMENT_PER_SUBSCRIPTION_JOIN

        assertTrue(join.contains("oldest_payment_id"))
        assertTrue(join.contains("GROUP_CONCAT"))
        assertTrue(join.contains("billing_date_datetime ASC"))
    }
}
