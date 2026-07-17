package com.dscorp.wispadmin.oltgateway.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SignalCategoryCalculatorTest {

    private val calculator = SignalCategoryCalculator()

    @Test
    fun `good cuando onuRx es mayor o igual a -25`() {
        assertEquals(SignalCategory.GOOD, calculator.fromOnuRxDbm(-25.0))
        assertEquals(SignalCategory.GOOD, calculator.fromOnuRxDbm(-18.54))
        assertEquals(SignalCategory.GOOD, calculator.fromOnuRxDbm(0.0))
    }

    @Test
    fun `warning cuando onuRx es mayor o igual a -27 y menor a -25`() {
        assertEquals(SignalCategory.WARNING, calculator.fromOnuRxDbm(-25.01))
        assertEquals(SignalCategory.WARNING, calculator.fromOnuRxDbm(-26.0))
        assertEquals(SignalCategory.WARNING, calculator.fromOnuRxDbm(-27.0))
    }

    @Test
    fun `critical cuando onuRx es menor a -27`() {
        assertEquals(SignalCategory.CRITICAL, calculator.fromOnuRxDbm(-27.01))
        assertEquals(SignalCategory.CRITICAL, calculator.fromOnuRxDbm(-30.46))
    }

    @Test
    fun `null cuando no hay rx`() {
        assertNull(calculator.fromOnuRxDbm(null))
    }

    @Test
    fun `value serializa a lowercase para persistencia`() {
        assertEquals("good", SignalCategory.GOOD.value)
        assertEquals("warning", SignalCategory.WARNING.value)
        assertEquals("critical", SignalCategory.CRITICAL.value)
    }
}
