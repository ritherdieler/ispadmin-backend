package com.dscorp.wispadmin.wispadmin.scheduled

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

object ServiceCutSchedule {

    fun cutDateFor(yearMonth: YearMonth): LocalDate {
        val fifteenth = yearMonth.atDay(15)
        return when (fifteenth.dayOfWeek) {
            DayOfWeek.SATURDAY -> fifteenth.plusDays(2)
            DayOfWeek.SUNDAY -> fifteenth.plusDays(1)
            else -> fifteenth
        }
    }

    fun shouldRun(today: LocalDate): Boolean = today == cutDateFor(YearMonth.from(today))
}
