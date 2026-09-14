package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.Face_data
import org.springframework.stereotype.Service
import com.dscorp.wispadmin.wispadmin.repository.FaceDataRepository
@Service
class FaceDataService(private val repository: FaceDataRepository) {
    fun findAll(): List<Face_data> = repository.findAll()
    fun save(faceData: Face_data) = repository.save(faceData)
    fun findByUserId(id: Int) = repository.findTopByUser_IdOrderByCreatedAtDesc(id)
    fun delete(id: Int) = repository.deleteById(id)
}
