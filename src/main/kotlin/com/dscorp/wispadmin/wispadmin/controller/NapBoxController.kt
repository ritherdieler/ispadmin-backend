package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.NapBox
import com.dscorp.wispadmin.wispadmin.dto.NapBoxDto
import com.dscorp.wispadmin.wispadmin.repository.NapBoxRepository
import com.dscorp.wispadmin.wispadmin.service.NapBoxService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.lang.Exception


@RestController
@RequestMapping("/napbox")
class NapBoxController {

    val objectErrorResponse: ResponseEntity<NapBoxDto> = ResponseEntity.status(500).body(null)
    val listObjectErrorResponse: ResponseEntity<List<NapBoxDto>> = ResponseEntity.status(500).body(null)

    @Autowired
    lateinit var repository: NapBoxRepository
    
    @Autowired
    lateinit var napBoxService: NapBoxService

    @PostMapping
    fun save(@RequestBody newNapBox: NapBox): ResponseEntity<NapBoxDto> {
        return try {
            val napBox = napBoxService.save(newNapBox)
            ResponseEntity.status(200).body(napBox)
        } catch (e: Exception) {
            e.printStackTrace()
            objectErrorResponse
        }
    }

    @GetMapping
    fun findAll(): ResponseEntity<List<NapBoxDto>> {
        return try {
            val napList = napBoxService.findAll()
            ResponseEntity.status(200).body(napList)
        } catch (e: Exception) {
            e.printStackTrace()
            listObjectErrorResponse
        }
    }

    @GetMapping("/near")
    fun findAllOrderedByNearToLocation(
        @RequestParam latitude: Float,
        @RequestParam longitude: Float
    ): ResponseEntity<List<NapBoxDto>> {
        return try {
            val napList = napBoxService.findAllOrderedByNearToLocation(latitude, longitude)
            ResponseEntity.status(200).body(napList)
        } catch (e: Exception) {
            e.printStackTrace()
            listObjectErrorResponse
        }
    }

    fun MutableList<NapBox>.toDtoList(): List<NapBoxDto> = this.map { it.toNapBoxWitPlaceDto() }
}
