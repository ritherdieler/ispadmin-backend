package com.dscorp.wispadmin.wispadmin.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException

class SubscriptionIntegrityViolationClassifierTest {

    private val classifier = SubscriptionIntegrityViolationClassifier()

    @Test
    fun `detects mysql duplicate on subscription ip column`() {
        val ex = DataIntegrityViolationException(
            "could not execute statement",
            RuntimeException("Duplicate entry '192.168.1.77' for key 'subscription.ip'")
        )

        assertTrue(classifier.isIpUniqueViolation(ex))
        assertEquals(
            SubscriptionIntegrityViolationClassifier.ViolationType.IP,
            classifier.classify(ex)
        )
    }

    @Test
    fun `detects hibernate uk constraint mentioning ip`() {
        val ex = DataIntegrityViolationException(
            "constraint [uk_subscription_ip]",
            RuntimeException("Duplicate entry '10.0.0.15' for key 'UK_subscription_ip'")
        )

        assertTrue(classifier.isIpUniqueViolation(ex))
    }

    @Test
    fun `dni unique is not ip conflict`() {
        val ex = DataIntegrityViolationException(
            "could not execute statement",
            RuntimeException("Duplicate entry '12345678' for key 'subscription.dni'")
        )

        assertFalse(classifier.isIpUniqueViolation(ex))
        assertEquals(
            SubscriptionIntegrityViolationClassifier.ViolationType.OTHER,
            classifier.classify(ex)
        )
    }

    @Test
    fun `client_request_id unique is not ip conflict`() {
        val ex = DataIntegrityViolationException(
            "could not execute statement",
            RuntimeException(
                "Duplicate entry 'offline-req-1' for key 'uk_subscription_client_request_id'"
            )
        )

        assertFalse(classifier.isIpUniqueViolation(ex))
    }
}
