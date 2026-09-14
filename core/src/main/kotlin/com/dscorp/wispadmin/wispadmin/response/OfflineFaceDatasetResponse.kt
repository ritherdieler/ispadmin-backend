package com.dscorp.wispadmin.wispadmin.response

import java.util.Date

data class OfflineFaceDatasetResponse(
    val datasetVersion: Long,
    val generatedAt: Date,
    val metric: String,
    val threshold: Double,
    val minMargin: Double,
    val descriptorSize: Int,
    val faces: List<OfflineFaceItemResponse>
)

data class OfflineFaceItemResponse(
    val faceDataId: Int,
    val userId: Int,
    val userName: String,
    val userDni: String?,
    val userType: String?,
    val faceEmbedding: List<Double>,
    val registeredAt: Date
)
