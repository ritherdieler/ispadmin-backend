package com.dscorp.wispadmin.wispadmin.data.model

import com.dscorp.wispadmin.wispadmin.dto.AssistanceTicketDto
import com.dscorp.wispadmin.wispadmin.dto.AttendanceDto
import java.util.Date
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.OneToMany
import javax.persistence.OneToOne

@Entity
data class Attendance(
    @Id
    @GeneratedValue
    var id:Int = 0,// Default para poder crear Attendance sin enviar id desde JSON. // JPA
    val checkIn: Date,// Check-in (cuando se crea)
    var checkOut: Date? = null,// Debe ser 'var' para actualizar al marcar salida. // JPA
    val method: String = "FACIAL",// Método (FACIAL/MANUAL)
    val status: String = "ACTIVO",// Estado

    @OneToOne
    val user: User //usuario asociado
) {
    fun toDto(): AttendanceDto {
        return AttendanceDto(
            id = id,
            checkIn = checkIn,
            checkOut = checkOut,
            method = method,
            status = status,
            userId = user.id
        )
    }
}