package com.dscorp.wispadmin.wispadmin.data.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import javax.persistence.FetchType
import javax.persistence.ManyToOne

class PaymentSubscriptionFetchTypeTest {

    @Test
    fun `Payment subscription association is lazy loaded`() {
        val field = Payment::class.java.getDeclaredField("subscription")
        val annotation = field.getAnnotation(ManyToOne::class.java)
        assertEquals(FetchType.LAZY, annotation.fetch)
    }
}
