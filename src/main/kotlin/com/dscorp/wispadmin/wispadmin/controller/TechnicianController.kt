package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.User
import com.dscorp.wispadmin.wispadmin.dto.UserDto
import com.dscorp.wispadmin.wispadmin.mapper.toDto
import com.dscorp.wispadmin.wispadmin.repository.UserRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/technician")
class TechnicianController {

    val objectErrorResponse: ResponseEntity<UserDto> = ResponseEntity.status(500).body(null)
    val listObjectErrorResponse: ResponseEntity<List<UserDto>> = ResponseEntity.status(500).body(null)

    @Autowired
    lateinit var repository: UserRepository

    @PostMapping
    fun registerTechnician(@RequestBody newTechnician: User): ResponseEntity<UserDto> {
        return try {
            val technician = repository.save(newTechnician)
            ResponseEntity.status(200).body(technician.toDto())
        } catch (e: Exception) {
            e.printStackTrace()
            objectErrorResponse
        }
    }


    @GetMapping
    fun getTechnicians(): ResponseEntity<List<UserDto>> {
        return try {
            val technicians = repository.getTechniciansByType(User.UserType.TECHNICIAN)
            ResponseEntity.status(200).body(technicians.map { it.toDto() })
        } catch (e: Exception) {
            e.printStackTrace()
            listObjectErrorResponse
        }
    }

}

private fun MutableList<User>.toDtoList(): List<UserDto> = this.map { it.toDto() }
