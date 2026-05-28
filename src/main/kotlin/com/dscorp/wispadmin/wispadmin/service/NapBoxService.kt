package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.NapBox
import com.dscorp.wispadmin.wispadmin.dto.NapBoxDto
import com.dscorp.wispadmin.wispadmin.repository.NapBoxRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import kotlin.math.pow
import kotlin.math.sqrt

@Service
class NapBoxService {

    @Autowired
    lateinit var repository: NapBoxRepository

    fun findAll(): List<NapBoxDto> {
        return repository.findAll().toDtoList()
    }

    fun save(napBox: NapBox): NapBoxDto {
        return repository.save(napBox).toDto()
    }

    fun findAllOrderedByNearToLocation(latitude: Float, longitude: Float): List<NapBoxDto> {
        val napBoxes = repository.findAll()
        
        // Ordenamos por distancia euclidiana (del punto más cercano al más lejano)
        return napBoxes
            .filter { it.latitude != null && it.longitude != null }
            .sortedBy { napBox ->
                calculateDistance(
                    latitude, 
                    longitude, 
                    napBox.latitude ?: 0f, 
                    napBox.longitude ?: 0f
                )
            }
            .toDtoList()
    }
    
    private fun calculateDistance(lat1: Float, lon1: Float, lat2: Float, lon2: Float): Double {
        // Usamos la distancia euclidiana para ordenar por cercanía
        return sqrt((lat2 - lat1).toDouble().pow(2) + (lon2 - lon1).toDouble().pow(2))
    }
    
    private fun List<NapBox>.toDtoList(): List<NapBoxDto> = this.map { it.toNapBoxWitPlaceDto() }
}
