package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.Mufa
import com.dscorp.wispadmin.wispadmin.dto.MufaDto
import com.dscorp.wispadmin.wispadmin.repository.MufaRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/mufa")
class MufaController(
    private val repository: MufaRepository
) {

    @PostMapping
    fun register(@RequestBody newMufa: Mufa): ResponseEntity<MufaDto> =
        ResponseEntity.ok(repository.save(newMufa).toDto())

    @GetMapping
    fun getAll(): ResponseEntity<List<MufaDto>> =
        ResponseEntity.ok(repository.findAll().map { it.toDto() })
}
