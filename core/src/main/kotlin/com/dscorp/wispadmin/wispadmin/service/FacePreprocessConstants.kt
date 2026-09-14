package com.dscorp.wispadmin.wispadmin.service

object FacePreprocessConstants {
    const val EMBEDDING_INPUT_SIZE = 224

    val CENTER_CROP_SPECS = listOf(
        CenterCropSpec(widthRatio = 0.82, heightRatio = 0.92, centerYRatio = 0.48),
        CenterCropSpec(widthRatio = 0.68, heightRatio = 0.82, centerYRatio = 0.44),
        CenterCropSpec(widthRatio = 0.96, heightRatio = 0.96, centerYRatio = 0.50)
    )

    data class CenterCropSpec(
        val widthRatio: Double,
        val heightRatio: Double,
        val centerYRatio: Double
    )
}
