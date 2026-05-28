package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.Place
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
    fun findPlaceByLocation(@RequestParam("latitude") latitude: Double, @RequestParam("longitude") longitude: Double): ResponseEntity<PlaceDto> {
        return try {
            val place = repository.findPlaceContainingPoint(latitude, longitude)
            ResponseEntity.status(200).body(place?.toDto() ?: throw Exception("Place not found"))
        } catch (e: Exception) {
            e.printStackTrace()
            objectErrorResponse
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
