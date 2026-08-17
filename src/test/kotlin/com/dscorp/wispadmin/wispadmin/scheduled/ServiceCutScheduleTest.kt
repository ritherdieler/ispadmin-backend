package com.dscorp.wispadmin.wispadmin.scheduled

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

class ServiceCutScheduleTest {

    @Test
    fun `cutDateFor uses the 15th when it is a weekday`() {
        val july2026 = YearMonth.of(2026, 7)

        assertEquals(LocalDate.of(2026, 7, 15), ServiceCutSchedule.cutDateFor(july2026))
        assertTrue(ServiceCutSchedule.shouldRun(LocalDate.of(2026, 7, 15)))
        assertFalse(ServiceCutSchedule.shouldRun(LocalDate.of(2026, 7, 16)))
    }

    @Test
    fun `cutDateFor uses the following Monday when the 15th is Saturday`() {
        val august2026 = YearMonth.of(2026, 8)

        assertEquals(LocalDate.of(2026, 8, 17), ServiceCutSchedule.cutDateFor(august2026))
        assertFalse(ServiceCutSchedule.shouldRun(LocalDate.of(2026, 8, 15)))
        assertFalse(ServiceCutSchedule.shouldRun(LocalDate.of(2026, 8, 16)))
        assertTrue(ServiceCutSchedule.shouldRun(LocalDate.of(2026, 8, 17)))
        assertFalse(ServiceCutSchedule.shouldRun(LocalDate.of(2026, 8, 18)))
    }

    @Test
    fun `cutDateFor uses the following Monday when the 15th is Sunday`() {
        val november2026 = YearMonth.of(2026, 11)

        assertEquals(LocalDate.of(2026, 11, 16), ServiceCutSchedule.cutDateFor(november2026))
        assertFalse(ServiceCutSchedule.shouldRun(LocalDate.of(2026, 11, 15)))
        assertTrue(ServiceCutSchedule.shouldRun(LocalDate.of(2026, 11, 16)))
    }

    @Test
    fun `shouldRun is false on an unrelated Tuesday`() {
        assertFalse(ServiceCutSchedule.shouldRun(LocalDate.of(2026, 8, 18)))
        assertFalse(ServiceCutSchedule.shouldRun(LocalDate.of(2026, 9, 1)))
    }
}
