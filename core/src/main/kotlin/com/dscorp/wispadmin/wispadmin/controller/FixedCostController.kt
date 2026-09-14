package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.User
import com.dscorp.wispadmin.wispadmin.dto.FixedCostDto
import com.dscorp.wispadmin.wispadmin.repository.FixedCostRepository
import com.dscorp.wispadmin.wispadmin.repository.UserRepository
import com.dscorp.wispadmin.wispadmin.requestbody.FixedCostRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/fixed_cost")
class FixedCostController(
    private val fixedCostRepository: FixedCostRepository,
    private val userRepository: UserRepository
) {

    @PostMapping("/")
    fun createFixedCost(@RequestBody request: FixedCostRequest): ResponseEntity<FixedCostDto> {
        val user = User(id = request.userId)
        val fixedCost = request.toFixedCost(user)
        val savedFixedCost = fixedCostRepository.save(fixedCost)
        return ResponseEntity.ok(savedFixedCost.toDto())
    }

    @GetMapping("/")
    fun getAllFixedCosts(): ResponseEntity<List<FixedCostDto>> =
        ResponseEntity.ok(fixedCostRepository.findByEnabledTrue().map { it.toDto() })

    @DeleteMapping("/{id}")
    fun deleteFixedCost(@PathVariable id: Int): ResponseEntity<Void> =
        fixedCostRepository.findById(id).map { fixedCost ->
            fixedCostRepository.delete(fixedCost)
            ResponseEntity<Void>(HttpStatus.OK)
        }.orElse(ResponseEntity.notFound().build())

    @PutMapping("/{id}")
    fun updateFixedCost(@PathVariable id: Int, @RequestBody request: FixedCostRequest): ResponseEntity<FixedCostDto> {
        val existing = fixedCostRepository.findById(id)
        if (!existing.isPresent) return ResponseEntity.notFound().build()
        val user = User(id = request.userId)
        val updated = existing.get().copy(
            amount = request.amount,
            description = request.description,
            note = request.note,
            type = request.type,
            user = user
        )
        val saved = fixedCostRepository.save(updated)
        return ResponseEntity.ok(saved.toDto())
    }
}
