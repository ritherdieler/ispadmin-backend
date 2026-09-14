package com.dscorp.wispadmin.wispadmin.util

import kotlin.math.sqrt

object FaceEmbeddingMath {

    fun averageL2Normalized(embeddings: List<List<Double>>): List<Double>? {
        if (embeddings.isEmpty()) return null
        val dimension = embeddings.first().size
        if (dimension == 0 || embeddings.any { it.size != dimension }) return null

        val sum = DoubleArray(dimension)
        for (embedding in embeddings) {
            for (index in 0 until dimension) {
                sum[index] += embedding[index]
            }
        }

        val averaged = DoubleArray(dimension) { index -> sum[index] / embeddings.size }
        return l2Normalize(averaged.toList())
    }

    fun l2Normalize(values: List<Double>): List<Double> {
        var norm = 0.0
        for (value in values) {
            norm += value * value
        }
        norm = sqrt(norm)
        if (norm == 0.0) return values
        return values.map { it / norm }
    }

    fun qualityWeight(
        brightness: Double,
        skinRatio: Double,
        edgeRatio: Double,
        minBrightness: Double,
        maxBrightness: Double,
        minSkinRatio: Double,
        minEdgeRatio: Double
    ): Double {
        val optimalBrightness = (minBrightness + maxBrightness) / 2.0
        val brightnessSpan = (maxBrightness - minBrightness).coerceAtLeast(1.0)
        val brightnessScore = 1.0 - (kotlin.math.abs(brightness - optimalBrightness) / brightnessSpan).coerceIn(0.0, 1.0)

        val skinScore = (skinRatio / (minSkinRatio * 4.0)).coerceIn(0.0, 1.0)
        val edgeScore = (edgeRatio / (minEdgeRatio * 6.0)).coerceIn(0.0, 1.0)

        return (brightnessScore * 0.35 + skinScore * 0.30 + edgeScore * 0.35).coerceIn(0.0, 1.0)
    }
}
