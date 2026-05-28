package com.dscorp.wispadmin.wispadmin.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.abs

@Service
class FacePhotoQualityService {
    private val logger = LoggerFactory.getLogger(FacePhotoQualityService::class.java)

    companion object {
        private const val MIN_BRIGHTNESS = 35.0
        private const val MAX_BRIGHTNESS = 235.0
        private const val MIN_SKIN_RATIO = 0.018
        private const val MIN_EDGE_RATIO = 0.012
    }

    // Valida que la foto tenga condiciones minimas de rostro antes de generar/comparar embeddings.
    fun hasUsableFaceCandidate(photoBytes: ByteArray): Boolean {
        val image = readImage(photoBytes) ?: return false
        if (image.width < 80 || image.height < 80) return false

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

        if (totalPixels == 0) return false

        val brightness = brightnessSum / totalPixels
        val skinRatio = skinPixels.toDouble() / totalPixels
        val edgeRatio = edgePixels.toDouble() / totalPixels

        val valid = brightness in MIN_BRIGHTNESS..MAX_BRIGHTNESS &&
            skinRatio >= MIN_SKIN_RATIO &&
            edgeRatio >= MIN_EDGE_RATIO

        if (!valid) {
            logger.info(
                "Foto facial rechazada por validacion previa. brightness={}, skinRatio={}, edgeRatio={}",
                brightness,
                skinRatio,
                edgeRatio
            )
        }

        return valid
    }

    private fun readImage(photoBytes: ByteArray): BufferedImage? {
        return try {
            ImageIO.read(ByteArrayInputStream(photoBytes))
        } catch (e: Exception) {
            null
        }
    }

    // Evalua principalmente el centro, que coincide con el recuadro donde el usuario coloca el rostro.
    private fun centralBounds(image: BufferedImage): ImageBounds {
        val width = image.width
        val height = image.height
        val left = (width * 0.18).toInt()
        val right = (width * 0.82).toInt()
        val top = (height * 0.08).toInt()
        val bottom = (height * 0.92).toInt()
        return ImageBounds(left, top, right, bottom)
    }

    // Rango amplio de piel en YCbCr/RGB para evitar aceptar fondos planos sin rostro.
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
