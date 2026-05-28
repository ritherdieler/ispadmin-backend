package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.FixedCost
import com.dscorp.wispadmin.wispadmin.data.model.User
import com.dscorp.wispadmin.wispadmin.config.HttpFailureContext
import com.dscorp.wispadmin.wispadmin.dto.FixedCostDto
import com.dscorp.wispadmin.wispadmin.logging.StackTraceSummarizer
import com.dscorp.wispadmin.wispadmin.repository.FixedCostRepository
import com.dscorp.wispadmin.wispadmin.repository.UserRepository
import com.dscorp.wispadmin.wispadmin.requestbody.FixedCostRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/fixed_cost")
class FixedCostController @Autowired constructor(
    private val fixedCostRepository: FixedCostRepository,
    private val userRepository: UserRepository
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    // Create
    @PostMapping("/")
    fun createFixedCost(@RequestBody request: FixedCostRequest): ResponseEntity<FixedCostDto> {
            val user = User(id = request.userId)
            val fixedCost = request.toFixedCost(user)
            val savedFixedCost = fixedCostRepository.save(fixedCost)
            return ResponseEntity.ok(savedFixedCost.toDto())
    }

    // Read all
    @GetMapping("/")
    fun getAllFixedCosts(): ResponseEntity<List<FixedCostDto>> {
        val fixedCosts = fixedCostRepository.findByEnabledTrue()
        return ResponseEntity.ok(fixedCosts.map { it.toDto() })
    }

    // Delete
    @DeleteMapping("/{id}")
    fun deleteFixedCost(@PathVariable id: Int): ResponseEntity<Void> {
        println("Attempting to delete fixed cost with ID: $id")
        return fixedCostRepository.findById(id).map { fixedCost ->
            println("Found fixed cost: ${fixedCost.description}, deleting...")
            fixedCostRepository.delete(fixedCost)
            println("Fixed cost deleted successfully")
            ResponseEntity<Void>(HttpStatus.OK)
        }.orElse(ResponseEntity.notFound().build())
    }

    // Update
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

    // Exception handler
    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception, request: HttpServletRequest): ResponseEntity<String> {
        request.setAttribute(HttpFailureContext.ATTR_STACK_SUMMARY, StackTraceSummarizer.summarize(e))
        log.error("Error en FixedCostController", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.message)
    }
}
