package com.dscorp.wispadmin.wispadmin.requestbody

// DTO tolerante para registrar rostro.
// Acepta el flujo nuevo recomendado: { userId, descriptor }
// Tambien acepta el flujo actual del frontend: { faceEmbedding, user: { id } }
data class SaveFaceDataBody(
    val userId: Int? = null,
    val descriptor: List<Double>? = null,
    val faceEmbedding: String? = null,
    val imageUrl: String? = null,
    val user: UserRef? = null
) {
    data class UserRef(
        val id: Int? = null
    )
}
