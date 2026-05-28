package com.dscorp.wispadmin.wispadmin.repository
import com.dscorp.wispadmin.wispadmin.data.model.Face_data
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
@Repository
interface FaceDataRepository: JpaRepository<Face_data, Int> {
    fun findByUser_Id(userId: Int): Face_data?
}
