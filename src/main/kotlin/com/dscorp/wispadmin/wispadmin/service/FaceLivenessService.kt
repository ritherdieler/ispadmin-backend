package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.FaceRecognitionProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.abs

@Service
class FaceLivenessService(
    private val faceRecognitionProperties: FaceRecognitionProperties
) {
    private val logger = LoggerFactory.getLogger(FaceLivenessService::class.java)

    fun evaluate(photoBytes: ByteArray): LivenessResult {
        if (!faceRecognitionProperties.liveness.enabled) {
            return LivenessResult(live = true, laplacianVariance = 0.0, colorVariance = 0.0)
        }

        val image = readImage(photoBytes)
            ?: return LivenessResult(live = false, laplacianVariance = 0.0, colorVariance = 0.0, reason = "INVALID_IMAGE")

        val bounds = centralBounds(image)
        val laplacianVariance = computeLaplacianVariance(image, bounds)
        val colorVariance = computeColorVariance(image, bounds)
        val liveness = faceRecognitionProperties.liveness

        val sharpEnough = laplacianVariance >= liveness.minLaplacianVariance
        val colorRichEnough = colorVariance >= liveness.minColorVariance
        val moireRatio = if (liveness.screenDetectionEnabled) computeMoireRatio(image, bounds) else 0.0
        val looksLikeScreen = liveness.screenDetectionEnabled && moireRatio > liveness.maxMoireRatio

        val live = sharpEnough && colorRichEnough && !looksLikeScreen
        val reason = when {
            !sharpEnough -> "LOW_TEXTURE"
            !colorRichEnough -> "FLAT_COLOR"
            looksLikeScreen -> "SCREEN_REPLAY"
            else -> null
        }

        if (!live) {
            logger.info(
                "Liveness pasivo rechazado. laplacian={}, colorVariance={}, moireRatio={}, reason={}",
                laplacianVariance,
                colorVariance,
                moireRatio,
                reason
            )
        }

        return LivenessResult(
            live = live,
            laplacianVariance = laplacianVariance,
            colorVariance = colorVariance,
            moireRatio = moireRatio,
            reason = reason
        )
    }

    data class LivenessResult(
        val live: Boolean,
        val laplacianVariance: Double,
        val colorVariance: Double,
        val moireRatio: Double = 0.0,
        val reason: String? = null
    )

    private fun readImage(photoBytes: ByteArray): BufferedImage? {
        return try {
            ImageIO.read(ByteArrayInputStream(photoBytes))
        } catch (e: Exception) {
            null
        }
    }

    private fun centralBounds(image: BufferedImage): ImageBounds {
        val width = image.width
        val height = image.height
        return ImageBounds(
            left = (width * 0.18).toInt(),
            top = (height * 0.08).toInt(),
            right = (width * 0.82).toInt(),
            bottom = (height * 0.92).toInt()
        )
    }

    private fun computeLaplacianVariance(image: BufferedImage, bounds: ImageBounds): Double {
        var sum = 0.0
        var sumSquares = 0.0
        var count = 0

        for (y in bounds.top + 1 until bounds.bottom - 1 step 2) {
            for (x in bounds.left + 1 until bounds.right - 1 step 2) {
                val center = luma(image.getRGB(x, y))
                val left = luma(image.getRGB(x - 1, y))
                val right = luma(image.getRGB(x + 1, y))
                val top = luma(image.getRGB(x, y - 1))
                val bottom = luma(image.getRGB(x, y + 1))
                val laplacian = abs(4 * center - left - right - top - bottom).toDouble()

                sum += laplacian
                sumSquares += laplacian * laplacian
                count++
            }
        }

        if (count == 0) return 0.0
        val mean = sum / count
        return (sumSquares / count) - (mean * mean)
    }

    private fun computeColorVariance(image: BufferedImage, bounds: ImageBounds): Double {
        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var count = 0

        for (y in bounds.top until bounds.bottom step 3) {
            for (x in bounds.left until bounds.right step 3) {
                val rgb = image.getRGB(x, y)
                sumR += (rgb shr 16 and 0xFF)
                sumG += (rgb shr 8 and 0xFF)
                sumB += (rgb and 0xFF)
                count++
            }
        }

        if (count == 0) return 0.0

        val meanR = sumR / count
        val meanG = sumG / count
        val meanB = sumB / count
        var variance = 0.0

        for (y in bounds.top until bounds.bottom step 3) {
            for (x in bounds.left until bounds.right step 3) {
                val rgb = image.getRGB(x, y)
                val r = (rgb shr 16 and 0xFF).toDouble()
                val g = (rgb shr 8 and 0xFF).toDouble()
                val b = (rgb and 0xFF).toDouble()
                variance += abs(r - meanR) + abs(g - meanG) + abs(b - meanB)
            }
        }

        return variance / count
    }

    // Mide cuan frecuentemente alterna el signo del gradiente horizontal de luminancia. Las fotos de
    // pantallas (moire) producen un zig-zag de alta frecuencia y dan un ratio cercano a 1; los rostros
    // reales tienen gradientes mas suaves y un ratio bajo.
    private fun computeMoireRatio(image: BufferedImage, bounds: ImageBounds): Double {
        val epsilon = 4
        var comparisons = 0
        var alternations = 0

        for (y in bounds.top until bounds.bottom step 2) {
            var previousSign = 0
            for (x in bounds.left + 1 until bounds.right) {
                val diff = luma(image.getRGB(x, y)) - luma(image.getRGB(x - 1, y))
                val sign = when {
                    diff > epsilon -> 1
                    diff < -epsilon -> -1
                    else -> 0
                }
                if (sign == 0) continue
                if (previousSign != 0) {
                    comparisons++
                    if (sign != previousSign) alternations++
                }
                previousSign = sign
            }
        }

        if (comparisons == 0) return 0.0
        return alternations.toDouble() / comparisons
    }

    private fun luma(rgb: Int): Int {
        val r = rgb shr 16 and 0xFF
        val g = rgb shr 8 and 0xFF
        val b = rgb and 0xFF
        return ((0.299 * r) + (0.587 * g) + (0.114 * b)).toInt()
    }

    private data class ImageBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )
}
