package com.dscorp.wispadmin.wispadmin.repository
import com.dscorp.wispadmin.wispadmin.data.model.Attendance
//import com.dscorp.wispadmin.wispadmin.data.model.AttendanceLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Date
@Repository
interface AttendanceRepository: JpaRepository<Attendance, Int>{
    //Busca el ultimo attendance sin salida (checkOut IS NULL). //check-out
    fun findTopByUser_IdAndCheckOutIsNullOrderByCheckInDesc(userId: Int): Attendance?
    fun findTopByUser_IdAndCheckInBetweenOrderByCheckInDesc(userId: Int, from: Date, to: Date): Attendance?
}
