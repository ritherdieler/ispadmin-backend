package com.dscorp.wispadmin.wispadmin.service

import ai.djl.modality.cv.Image
import ai.djl.modality.cv.output.BoundingBox
import ai.djl.modality.cv.output.DetectedObjects
import ai.djl.modality.cv.output.Landmark
import ai.djl.modality.cv.output.Point
import ai.djl.ndarray.NDArray
import ai.djl.ndarray.NDArrays
import ai.djl.ndarray.NDList
import ai.djl.ndarray.NDManager
import ai.djl.ndarray.types.DataType
import ai.djl.ndarray.types.Shape
import ai.djl.translate.Translator
import ai.djl.translate.TranslatorContext
import kotlin.math.ceil

/**
 * Traduce las entradas y salidas del modelo liviano ultranet.
 *
 * El detector no identifica usuarios. Su unica responsabilidad es localizar
 * rostros reales en una fotografia y devolver su caja junto con cinco puntos
 * faciales para que otro servicio pueda recortar y alinear la imagen.
 */
class FaceDetectionTranslator(
    private val confidenceThreshold: Double,
    private val nmsThreshold: Double,
    private val variance: DoubleArray,
    private val topK: Int,
    private val scales: Array<IntArray>,
    private val steps: IntArray
) : Translator<Image, DetectedObjects> {

    /**
     * Convierte la imagen RGB a un tensor BGR y aplica la normalizacion
     * esperada por ultranet antes de ejecutar la inferencia.
     */
    override fun processInput(ctx: TranslatorContext, input: Image): NDList {
        ctx.setAttachment("width", input.width)
        ctx.setAttachment("height", input.height)

        var array = input.toNDArray(ctx.ndManager, Image.Flag.COLOR)
        array = array.transpose(2, 0, 1).flip(0)

        if (array.dataType != DataType.FLOAT32) {
            array = array.toType(DataType.FLOAT32, false)
        }

        val mean = ctx.ndManager.create(
            floatArrayOf(104f, 117f, 123f),
            Shape(3, 1, 1)
        )

        return NDList(array.sub(mean))
    }

    /**
     * Interpreta las predicciones de ultranet, elimina detecciones con baja
     * confianza y conserva una sola caja cuando varias se superponen.
     */
    override fun processOutput(ctx: TranslatorContext, list: NDList): DetectedObjects {
        val width = ctx.getAttachment("width") as Int
        val height = ctx.getAttachment("height") as Int
        val manager = ctx.ndManager
        val scaleXY = variance[0]
        val scaleWH = variance[1]

        var probabilities = list[1].get(":, 1:")
        probabilities = NDArrays.stack(
            NDList(
                probabilities.argMax(1).toType(DataType.FLOAT32, false),
                probabilities.max(intArrayOf(1))
            )
        )

        val recoveredBoxes = recoverBoxes(manager, width, height)
        var boundingBoxes = list[0]
        val boundingBoxSize = boundingBoxes.get(":, 2:")
            .mul(scaleWH)
            .exp()
            .mul(recoveredBoxes.get(":, 2:"))
        val boundingBoxPosition = boundingBoxes.get(":, :2")
            .mul(scaleXY)
            .mul(recoveredBoxes.get(":, 2:"))
            .add(recoveredBoxes.get(":, :2"))
            .sub(boundingBoxSize.mul(0.5f))

        boundingBoxes = NDArrays.concat(
            NDList(boundingBoxPosition, boundingBoxSize),
            1
        )

        var landmarks = decodeLandmarks(list[2], recoveredBoxes, scaleXY)
        val accepted = probabilities.get(1).gt(confidenceThreshold)
        boundingBoxes = boundingBoxes.transpose().booleanMask(accepted, 1).transpose()
        landmarks = landmarks.transpose().booleanMask(accepted, 1).transpose()
        probabilities = probabilities.booleanMask(accepted, 1).transpose()

        val order = probabilities.get(":, 1")
            .argSort()
            .get(":$topK")
            .toLongArray()

        val names = mutableListOf<String>()
        val acceptedProbabilities = mutableListOf<Double>()
        val boxes = mutableListOf<BoundingBox>()
        val boxesByClass = mutableMapOf<Int, MutableList<BoundingBox>>()

        for (index in order.indices.reversed()) {
            val position = order[index]
            val classProbability = probabilities.get(position).toFloatArray()
            val classId = classProbability[0].toInt()
            val probability = classProbability[1].toDouble()
            val box = boundingBoxes.get(position).toDoubleArray()
            val landmarkCoordinates = landmarks.get(position).toDoubleArray()
            val rectangle = ai.djl.modality.cv.output.Rectangle(
                box[0],
                box[1],
                box[2],
                box[3]
            )

            val classBoxes = boxesByClass.getOrPut(classId) { mutableListOf() }
            val isNotOverlapping = classBoxes.none { it.getIoU(rectangle) > nmsThreshold }

            if (isNotOverlapping) {
                val points = mutableListOf<Point>()
                for (pointIndex in 0 until 5) {
                    points.add(
                        Point(
                            landmarkCoordinates[pointIndex * 2] * width,
                            landmarkCoordinates[pointIndex * 2 + 1] * height
                        )
                    )
                }

                val landmark = Landmark(
                    box[0],
                    box[1],
                    box[2],
                    box[3],
                    points
                )

                classBoxes.add(landmark)
                names.add("Face")
                acceptedProbabilities.add(probability)
                boxes.add(landmark)
            }
        }

        return DetectedObjects(names, acceptedProbabilities, boxes)
    }

    /**
     * Genera las cajas base que ultranet utiliza para reconstruir la ubicacion
     * de cada rostro detectado.
     */
    private fun recoverBoxes(manager: NDManager, width: Int, height: Int): NDArray {
        val aspectRatios = steps.map { step ->
            intArrayOf(
                ceil(height.toDouble() / step).toInt(),
                ceil(width.toDouble() / step).toInt()
            )
        }

        val boxes = mutableListOf<DoubleArray>()
        for (stepIndex in steps.indices) {
            val step = steps[stepIndex]
            for (row in 0 until aspectRatios[stepIndex][0]) {
                for (column in 0 until aspectRatios[stepIndex][1]) {
                    for (scale in scales[stepIndex]) {
                        boxes.add(
                            doubleArrayOf(
                                (column + 0.5) * step / width,
                                (row + 0.5) * step / height,
                                scale.toDouble() / width,
                                scale.toDouble() / height
                            )
                        )
                    }
                }
            }
        }

        return manager.create(boxes.toTypedArray()).clip(0.0, 1.0)
    }

    /**
     * Convierte la salida del modelo en los cinco puntos faciales:
     * ojos, nariz y extremos de la boca.
     */
    private fun decodeLandmarks(
        prediction: NDArray,
        recoveredBoxes: NDArray,
        scaleXY: Double
    ): NDArray {
        val point1 = prediction.get(":, :2")
            .mul(scaleXY)
            .mul(recoveredBoxes.get(":, 2:"))
            .add(recoveredBoxes.get(":, :2"))
        val point2 = prediction.get(":, 2:4")
            .mul(scaleXY)
            .mul(recoveredBoxes.get(":, 2:"))
            .add(recoveredBoxes.get(":, :2"))
        val point3 = prediction.get(":, 4:6")
            .mul(scaleXY)
            .mul(recoveredBoxes.get(":, 2:"))
            .add(recoveredBoxes.get(":, :2"))
        val point4 = prediction.get(":, 6:8")
            .mul(scaleXY)
            .mul(recoveredBoxes.get(":, 2:"))
            .add(recoveredBoxes.get(":, :2"))
        val point5 = prediction.get(":, 8:10")
            .mul(scaleXY)
            .mul(recoveredBoxes.get(":, 2:"))
            .add(recoveredBoxes.get(":, :2"))

        return NDArrays.concat(
            NDList(point1, point2, point3, point4, point5),
            1
        )
    }
}
