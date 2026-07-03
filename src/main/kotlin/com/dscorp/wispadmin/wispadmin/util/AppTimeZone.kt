package com.dscorp.wispadmin.wispadmin.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar
import java.util.TimeZone

object AppTimeZone {
    private const val DEFAULT = "America/Lima"

    @Volatile
    private var zoneId: ZoneId = ZoneId.of(DEFAULT)

    fun initialize(timezone: String) {
        zoneId = ZoneId.of(timezone)
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
    }

    fun zoneId(): ZoneId = zoneId

    fun calendar(): Calendar = Calendar.getInstance(TimeZone.getTimeZone(zoneId))

    fun currentBillingPeriod(): Pair<LocalDateTime, LocalDateTime> {
        val currentMonthStart = LocalDate.now(zoneId).withDayOfMonth(1).atStartOfDay()
        val previousMonthStart = currentMonthStart.minusMonths(1)
        return previousMonthStart to currentMonthStart
    }
}

fun spanishMonthShort(month: Int): String {
    return when (month) {
        1 -> "Ene."
        2 -> "Feb."
        3 -> "Mar."
        4 -> "Abr."
        5 -> "May."
        6 -> "Jun."
        7 -> "Jul."
        8 -> "Ago."
        9 -> "Set."
        10 -> "Oct."
        11 -> "Nov."
        12 -> "Dic."
        else -> "?"
    }
}
