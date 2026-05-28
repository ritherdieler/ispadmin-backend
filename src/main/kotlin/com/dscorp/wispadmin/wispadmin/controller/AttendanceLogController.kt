//package com.dscorp.wispadmin.wispadmin.controller
//
//import com.dscorp.wispadmin.wispadmin.data.model.AttendanceLog
//import com.dscorp.wispadmin.wispadmin.service.AttendanceLogService
//import org.springframework.web.bind.annotation.*
//
//@RestController
//@RequestMapping("/api/attendance-logs")
//class AttendanceLogController(private val service: AttendanceLogService) {
//
//    @GetMapping
//    fun verLogs() = service.findAll()
//
//    @PostMapping
//    fun crearLog(@RequestBody data: AttendanceLog) = service.save(data)
//
//    @DeleteMapping("/{id}")
//    fun borrarLog(@PathVariable id: Int) = service.delete(id)
//}