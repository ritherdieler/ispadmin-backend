package com.dscorp.wispadmin.wispadmin.service

import ai.djl.modality.cv.output.Landmark
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage

/**
 * Alinea un rostro a la plantilla canonica de ArcFace/InsightFace (112x112) usando
 * los 5 puntos de referencia (ojos, nariz, comisuras de la boca) y una transformada
 * de similitud (rotacion + escala + traslacion) por minimos cuadrados.
 *
 * Es el preprocesado correcto para modelos tipo ArcFace; reemplaza la heuristica de
 * distancia entre ojos de [FaceLandmarkAligner] cuando face.embedding.alignment=ARCFACE.
 */
object ArcFaceLandmarkAligner {

    // Plantilla canonica de 5 puntos para 112x112 (orden: ojo izq, ojo der, nariz, boca izq, boca der).
    private val TEMPLATE_112 = arrayOf(
        doubleArrayOf(38.2946, 51.6963),
        doubleArrayOf(73.5318, 51.5014),
        doubleArrayOf(56.0252, 71.7366),
        doubleArrayOf(41.5493, 92.3655),
        doubleArrayOf(70.7299, 92.2041)
    )

    fun alignFace(
        source: BufferedImage,
        landmark: Landmark,
        outputSize: Int = 112
    ): BufferedImage? {
        val points = landmark.path.toList()
        if (points.size < 5) return null

        val detected = Array(5) { index ->
            doubleArrayOf(points[index].x, points[index].y)
        }
        val template = scaledTemplate(outputSize)

        val transform = similarityTransform(detected, template) ?: return null

        val aligned = BufferedImage(outputSize, outputSize, BufferedImage.TYPE_3BYTE_BGR)
        val graphics = aligned.createGraphics()
        graphics.color = Color.BLACK
        graphics.fillRect(0, 0, outputSize, outputSize)
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.drawImage(source, transform, null)
        graphics.dispose()

        return aligned
    }

    private fun scaledTemplate(outputSize: Int): Array<DoubleArray> {
        val factor = outputSize / 112.0
        return Array(TEMPLATE_112.size) { index ->
            doubleArrayOf(TEMPLATE_112[index][0] * factor, TEMPLATE_112[index][1] * factor)
        }
    }

    /**
     * Solucion cerrada de la transformada de similitud 2D (escala + rotacion + traslacion)
     * por minimos cuadrados que mapea los puntos detectados a la plantilla destino.
     */
    private fun similarityTransform(
        from: Array<DoubleArray>,
        to: Array<DoubleArray>
    ): AffineTransform? {
        val n = from.size
        if (n == 0 || to.size != n) return null

        var meanFromX = 0.0
        var meanFromY = 0.0
        var meanToX = 0.0
        var meanToY = 0.0
        for (i in 0 until n) {
            meanFromX += from[i][0]
            meanFromY += from[i][1]
            meanToX += to[i][0]
            meanToY += to[i][1]
        }
        meanFromX /= n
        meanFromY /= n
        meanToX /= n
        meanToY /= n

        var sxx = 0.0
        var a = 0.0
        var b = 0.0
        for (i in 0 until n) {
            val px = from[i][0] - meanFromX
            val py = from[i][1] - meanFromY
            val qx = to[i][0] - meanToX
            val qy = to[i][1] - meanToY

            sxx += px * px + py * py
            a += px * qx + py * qy
            b += px * qy - py * qx
        }

        if (sxx == 0.0) return null

        val scaleCos = a / sxx
        val scaleSin = b / sxx

        val tx = meanToX - (scaleCos * meanFromX - scaleSin * meanFromY)
        val ty = meanToY - (scaleSin * meanFromX + scaleCos * meanFromY)

        // AffineTransform(m00, m10, m01, m11, m02, m12)
        return AffineTransform(scaleCos, scaleSin, -scaleSin, scaleCos, tx, ty)
    }
}
