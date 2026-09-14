package com.dscorp.wispadmin.wispadmin.requestbody

data class FaceEvidenceBody(
    val imageBase64: String = "",
    val reason: String = "",
    val userId: Int? = null
)
