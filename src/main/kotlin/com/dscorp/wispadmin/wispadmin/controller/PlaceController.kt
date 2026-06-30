package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.Place
import com.dscorp.wispadmin.wispadmin.data.model.util.BaseResponse
import com.dscorp.wispadmin.wispadmin.dto.PlaceDto
import com.dscorp.wispadmin.wispadmin.repository.PlaceRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/place")
class PlaceController {

    val objectErrorResponse: ResponseEntity<PlaceDto> = ResponseEntity.status(500).body(null)
    val listObjectErrorResponse: ResponseEntity<List<PlaceDto>> = ResponseEntity.status(500).body(null)

    @Autowired
    lateinit var repository: PlaceRepository

    @PostMapping
    fun registerPlace(@RequestBody newPlace: Place): ResponseEntity<PlaceDto> {
        return try {
            val place = repository.save(newPlace)
            ResponseEntity.status(200).body(place.toDto())
        } catch (e: Exception) {
            e.printStackTrace()
            objectErrorResponse
        }
    }


    @GetMapping("/findByLocation")
    fun findPlaceByLocation(
        @RequestParam("latitude") latitude: Double,
        @RequestParam("longitude") longitude: Double
    ): BaseResponse {
        return try {
            val place = repository.findPlaceContainingPoint(latitude, longitude)
            if (place == null) {
                BaseResponse(
                    status = 404,
                    error = "No se encontró un lugar para las coordenadas latitude=$latitude, longitude=$longitude"
                )
            } else {
                BaseResponse(status = 200, data = place.toDto())
            }
        } catch (e: Exception) {
            BaseResponse(
                status = 500,
                error = e.message ?: "Error al buscar lugar por ubicación"
            )
        }
    }

    @GetMapping
    fun getPlaces(): ResponseEntity<List<PlaceDto>> {
        return try {
            val places = repository.findAll()
            ResponseEntity.status(200).body(places.toDtoList())
        } catch (e: Exception) {
            e.printStackTrace()
            listObjectErrorResponse
        }
    }
}
