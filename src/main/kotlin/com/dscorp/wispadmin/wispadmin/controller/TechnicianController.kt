package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.User
import com.dscorp.wispadmin.wispadmin.dto.UserDto
import com.dscorp.wispadmin.wispadmin.mapper.toDto
import com.dscorp.wispadmin.wispadmin.repository.UserRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/technician")
class TechnicianController(
    private val repository: UserRepository
) {

    @PostMapping
    fun registerTechnician(@RequestBody newTechnician: User): ResponseEntity<UserDto> =
        ResponseEntity.ok(repository.save(newTechnician).toDto())

    @GetMapping
    fun getTechnicians(): ResponseEntity<List<UserDto>> =
        ResponseEntity.ok(repository.getTechniciansByType(User.UserType.TECHNICIAN).map { it.toDto() })
}
