package com.dscorp.wispadmin.wispadmin.service
import com.dscorp.wispadmin.wispadmin.data.model.Attendance
import com.dscorp.wispadmin.wispadmin.repository.AttendanceRepository
import org.springframework.stereotype.Service
@Service
class AttendanceService(private val repository: AttendanceRepository) {
    fun findAll(): List<Attendance> = repository.findAll()
    fun findById(id: Int) = repository.findById(id)
    fun save(attendance: Attendance) = repository.save(attendance)
    fun delete(id: Int) = repository.deleteById(id)

}