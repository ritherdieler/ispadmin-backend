package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.Place
import com.dscorp.wispadmin.wispadmin.data.model.util.BaseResponse
import com.dscorp.wispadmin.wispadmin.dto.PlaceDto
import com.dscorp.wispadmin.wispadmin.repository.PlaceRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/place")
class PlaceController(
    private val repository: PlaceRepository
) {

    @PostMapping
    fun registerPlace(@RequestBody newPlace: Place): ResponseEntity<PlaceDto> =
        ResponseEntity.ok(repository.save(newPlace).toDto())

    @GetMapping("/findByLocation")
    fun findPlaceByLocation(
        @RequestParam("latitude") latitude: Double,
        @RequestParam("longitude") longitude: Double
    ): BaseResponse {
        val place = repository.findPlaceContainingPoint(latitude, longitude)
        return if (place == null) {
            BaseResponse(
                status = 404,
                error = "No se encontró un lugar para las coordenadas latitude=$latitude, longitude=$longitude"
            )
        } else {
            BaseResponse(status = 200, data = place.toDto())
        }
    }

    @GetMapping
    fun getPlaces(): ResponseEntity<List<PlaceDto>> =
        ResponseEntity.ok(repository.findAll().toDtoList())
}
