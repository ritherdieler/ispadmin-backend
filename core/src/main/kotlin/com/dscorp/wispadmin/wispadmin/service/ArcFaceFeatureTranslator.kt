package com.dscorp.wispadmin.wispadmin.service

import ai.djl.modality.cv.Image
import ai.djl.modality.cv.util.NDImageUtils
import ai.djl.ndarray.NDList
import ai.djl.ndarray.types.DataType
import ai.djl.translate.Batchifier
import ai.djl.translate.Translator
import ai.djl.translate.TranslatorContext

/**
 * Traductor DJL para modelos de embeddings tipo ArcFace/InsightFace ejecutados con
 * ONNX Runtime. Redimensiona a inputSize x inputSize, pasa a CHW, normaliza con
 * (pixel/255 - mean) / std y opcionalmente aplica normalizacion L2 al embedding.
 *
 * Solo se usa cuando face.embedding.engine=OnnxRuntime.
 */
class ArcFaceFeatureTranslator(
    private val inputSize: Int,
    private val mean: FloatArray,
    private val std: FloatArray,
    private val l2Normalize: Boolean
) : Translator<Image, FloatArray> {

    override fun getBatchifier(): Batchifier = Batchifier.STACK

    override fun processInput(ctx: TranslatorContext, input: Image): NDList {
        var array = input.toNDArray(ctx.ndManager, Image.Flag.COLOR)
        array = NDImageUtils.resize(array, inputSize, inputSize)
        array = array.toType(DataType.FLOAT32, false).div(255f)
        array = array.transpose(2, 0, 1)
        array = NDImageUtils.normalize(array, mean, std)
        return NDList(array)
    }

    override fun processOutput(ctx: TranslatorContext, list: NDList): FloatArray {
        var result = list.singletonOrThrow()
        if (l2Normalize) {
            val norm = result.norm()
            result = result.div(norm)
        }
        return result.toType(DataType.FLOAT32, false).toFloatArray()
    }
}
