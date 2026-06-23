package com.dscorp.wispadmin.wispadmin.repository
import com.dscorp.wispadmin.wispadmin.data.model.Face_data
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
@Repository
interface FaceDataRepository: JpaRepository<Face_data, Int> {
    fun findAllByUser_Id(userId: Int): List<Face_data>
    fun findTopByUser_IdOrderByCreatedAtDesc(userId: Int): Face_data?
    fun existsByUser_Id(userId: Int): Boolean
    fun deleteAllByUser_Id(userId: Int)
}
