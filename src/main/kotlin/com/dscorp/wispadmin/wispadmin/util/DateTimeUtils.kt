package com.dscorp.wispadmin.wispadmin.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

// Convierte timestamps guardados en milisegundos a LocalDateTime.
// Si el valor viene null o 0, devuelve null para evitar fechas invalidas.
fun Long?.toLocalDateTimeOrNull(): LocalDateTime? {
    if (this == null || this == 0L) return null

    return LocalDateTime.ofInstant(
        Instant.ofEpochMilli(this),
        ZoneId.systemDefault()
    )
}