package com.dscorp.wispadmin.wispadmin.service

import ai.djl.modality.cv.output.Landmark
import ai.djl.modality.cv.output.Point
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import kotlin.math.atan2
import kotlin.math.hypot

object FaceLandmarkAligner {
    private const val TARGET_EYE_DISTANCE_RATIO = 0.36

    fun alignFace(
        source: BufferedImage,
        landmark: Landmark,
        outputSize: Int = FacePreprocessConstants.EMBEDDING_INPUT_SIZE
    ): BufferedImage? {
        val points = landmark.path.toList()
        if (points.size < 3) return null

        val leftEye = points[0]
        val rightEye = points[1]
        val nose = points.getOrNull(2) ?: midpoint(leftEye, rightEye)

        val eyeDistance = hypot(rightEye.x - leftEye.x, rightEye.y - leftEye.y)
        if (eyeDistance < 8.0) return null

        val angle = atan2(rightEye.y - leftEye.y, rightEye.x - leftEye.x)
        val eyeCenterX = (leftEye.x + rightEye.x) / 2.0
        val eyeCenterY = (leftEye.y + rightEye.y) / 2.0
        val targetEyeDistance = outputSize * TARGET_EYE_DISTANCE_RATIO
        val scale = targetEyeDistance / eyeDistance

        val noseOffsetY = nose.y - eyeCenterY
        val targetCenterY = outputSize * 0.42
        val targetCenterX = outputSize / 2.0

        val aligned = BufferedImage(outputSize, outputSize, BufferedImage.TYPE_3BYTE_BGR)
        val graphics = aligned.createGraphics()
        graphics.color = Color.BLACK
        graphics.fillRect(0, 0, outputSize, outputSize)
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

        val transform = AffineTransform()
        transform.translate(targetCenterX, targetCenterY)
        transform.rotate(-angle)
        transform.scale(scale, scale)
        transform.translate(-eyeCenterX, -(eyeCenterY + noseOffsetY * 0.35))

        graphics.drawImage(source, transform, null)
        graphics.dispose()

        return aligned
    }

    private fun midpoint(first: Point, second: Point): Point {
        return Point((first.x + second.x) / 2.0, (first.y + second.y) / 2.0)
    }
}
