package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.Mufa
import com.dscorp.wispadmin.wispadmin.dto.MufaDto
import com.dscorp.wispadmin.wispadmin.repository.MufaRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.lang.Exception


@RestController
@RequestMapping("/mufa")
class MufaController {

    val objectErrorResponse: ResponseEntity<MufaDto> = ResponseEntity.status(500).body(null)
    val listObjectErrorResponse: ResponseEntity<List<MufaDto>> = ResponseEntity.status(500).body(null)

    @Autowired
    lateinit var repository: MufaRepository

    @PostMapping
    fun register(@RequestBody newMufa: Mufa): ResponseEntity<MufaDto> {
        return try {
            val Mufa = repository.save(newMufa)
            ResponseEntity.status(200).body(Mufa.toDto())
        } catch (e: Exception) {
            e.printStackTrace()
            objectErrorResponse
        }
    }

    @GetMapping
    fun getAll(): ResponseEntity<List<MufaDto>> {
        return try {
            val mufaList = repository.findAll()
            ResponseEntity.status(200).body(mufaList.toDtoList())
        } catch (e: Exception) {
            e.printStackTrace()
            listObjectErrorResponse
        }
    }
     fun MutableList<Mufa>.toDtoList(): List<MufaDto> = this.map { it.toDto() }

}
