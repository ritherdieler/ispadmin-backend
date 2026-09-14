package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.dto.NapBoxDto
import com.dscorp.wispadmin.wispadmin.data.model.NapBox
import com.dscorp.wispadmin.wispadmin.service.NapBoxService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/napbox")
class NapBoxController(
    private val napBoxService: NapBoxService
) {

    @PostMapping
    fun save(@RequestBody newNapBox: NapBox): ResponseEntity<NapBoxDto> =
        ResponseEntity.ok(napBoxService.save(newNapBox))

    @GetMapping
    fun findAll(): ResponseEntity<List<NapBoxDto>> =
        ResponseEntity.ok(napBoxService.findAll())

    @GetMapping("/near")
    fun findAllOrderedByNearToLocation(
        @RequestParam latitude: Float,
        @RequestParam longitude: Float
    ): ResponseEntity<List<NapBoxDto>> =
        ResponseEntity.ok(napBoxService.findAllOrderedByNearToLocation(latitude, longitude))
}
