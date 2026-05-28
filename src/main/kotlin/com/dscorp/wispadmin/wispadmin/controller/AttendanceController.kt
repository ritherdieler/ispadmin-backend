package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.Attendance
import com.dscorp.wispadmin.wispadmin.dto.AttendanceDto
import com.dscorp.wispadmin.wispadmin.service.AttendanceService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/attendance")
@CrossOrigin(
    origins = ["http://localhost:5173"],
    methods = [RequestMethod.GET, RequestMethod.POST, RequestMethod.DELETE, RequestMethod.OPTIONS]
)
class AttendanceController(private val service: AttendanceService) {
    @GetMapping("getAll")
    fun getAll(): ResponseEntity<List<AttendanceDto>> {
        val attendances = service.findAll().map { it.toDto() }
        return ResponseEntity.ok(attendances)

    }

    @PostMapping("save")
    fun save(@RequestBody data: Attendance) = service.save(data)

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Int) = service.delete(id)

    @GetMapping("byUser/{userId}")
    fun byUser(
        @PathVariable userId: Int
    ): ResponseEntity<AttendanceDto> {
        val attendance = service.findById(userId)
        return ResponseEntity.ok(attendance.get().toDto())
    }
}
