package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.FaceRecognitionProperties
import com.dscorp.wispadmin.wispadmin.util.FaceEmbeddingMath
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.abs

@Service
class FacePhotoQualityService(
    private val faceRecognitionProperties: FaceRecognitionProperties
) {
    private val logger = LoggerFactory.getLogger(FacePhotoQualityService::class.java)

    fun hasUsableFaceCandidate(photoBytes: ByteArray): Boolean = evaluate(photoBytes).valid

    /**
     * Calcula las metricas de calidad de la foto y decide si es usable.
     * Expone los valores intermedios para poder registrarlos y analizarlos despues.
     */
    fun evaluate(photoBytes: ByteArray): QualityResult {
        val image = readImage(photoBytes)
            ?: return QualityResult(valid = false, width = 0, height = 0, brightness = 0.0, skinRatio = 0.0, edgeRatio = 0.0, qualityScore = 0.0)

        val minImageSize = faceRecognitionProperties.quality.minImageSize
        if (image.width < minImageSize || image.height < minImageSize) {
            return QualityResult(
                valid = false,
                width = image.width,
                height = image.height,
                brightness = 0.0,
                skinRatio = 0.0,
                edgeRatio = 0.0,
                qualityScore = 0.0
            )
        }

        val bounds = centralBounds(image)
        var totalPixels = 0
        var skinPixels = 0
        var brightnessSum = 0.0
        var edgePixels = 0

        var previousLuma: Int? = null
        for (y in bounds.top until bounds.bottom step 2) {
            previousLuma = null
            for (x in bounds.left until bounds.right step 2) {
                val rgb = image.getRGB(x, y)
                val r = rgb shr 16 and 0xFF
                val g = rgb shr 8 and 0xFF
                val b = rgb and 0xFF
                val luma = ((0.299 * r) + (0.587 * g) + (0.114 * b)).toInt()

                totalPixels++
                brightnessSum += luma
                if (isSkinLike(r, g, b)) skinPixels++

                previousLuma?.let {
                    if (abs(luma - it) > 22) edgePixels++
                }
                previousLuma = luma
            }
        }

        if (totalPixels == 0) {
            return QualityResult(
                valid = false,
                width = image.width,
                height = image.height,
                brightness = 0.0,
                skinRatio = 0.0,
                edgeRatio = 0.0,
                qualityScore = 0.0
            )
        }

        val brightness = brightnessSum / totalPixels
        val skinRatio = skinPixels.toDouble() / totalPixels
        val edgeRatio = edgePixels.toDouble() / totalPixels
        val quality = faceRecognitionProperties.quality

        val valid = brightness in quality.minBrightness..quality.maxBrightness &&
            skinRatio >= quality.minSkinRatio &&
            edgeRatio >= quality.minEdgeRatio

        if (!valid) {
            logger.info(
                "Foto facial rechazada por validacion previa. brightness={}, skinRatio={}, edgeRatio={}",
                brightness,
                skinRatio,
                edgeRatio
            )
        }

        val qualityScore = FaceEmbeddingMath.qualityWeight(
            brightness = brightness,
            skinRatio = skinRatio,
            edgeRatio = edgeRatio,
            minBrightness = quality.minBrightness,
            maxBrightness = quality.maxBrightness,
            minSkinRatio = quality.minSkinRatio,
            minEdgeRatio = quality.minEdgeRatio
        )

        return QualityResult(
            valid = valid,
            width = image.width,
            height = image.height,
            brightness = brightness,
            skinRatio = skinRatio,
            edgeRatio = edgeRatio,
            qualityScore = qualityScore
        )
    }

    data class QualityResult(
        val valid: Boolean,
        val width: Int,
        val height: Int,
        val brightness: Double,
        val skinRatio: Double,
        val edgeRatio: Double,
        val qualityScore: Double = 0.0
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
        val left = (width * 0.18).toInt()
        val right = (width * 0.82).toInt()
        val top = (height * 0.08).toInt()
        val bottom = (height * 0.92).toInt()
        return ImageBounds(left, top, right, bottom)
    }

    private fun isSkinLike(r: Int, g: Int, b: Int): Boolean {
        val cb = 128 - 0.168736 * r - 0.331264 * g + 0.5 * b
        val cr = 128 + 0.5 * r - 0.418688 * g - 0.081312 * b
        val rgbRule = r > 45 && g > 34 && b > 20 && r > b && r >= g - 15 && abs(r - g) > 8
        val ycbcrRule = cb in 75.0..135.0 && cr in 130.0..180.0
        return rgbRule && ycbcrRule
    }

    private data class ImageBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )
}
