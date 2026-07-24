package com.dscorp.wispadmin.wispadmin.service

import ai.djl.ModelException
import ai.djl.inference.Predictor
import ai.djl.modality.cv.Image
import ai.djl.modality.cv.ImageFactory
import ai.djl.modality.cv.translator.ImageFeatureExtractorFactory
import ai.djl.repository.zoo.Criteria
import ai.djl.repository.zoo.ZooModel
import ai.djl.training.util.ProgressBar
import ai.djl.translate.TranslateException
import com.dscorp.wispadmin.wispadmin.config.FaceEmbeddingProperties
import com.dscorp.wispadmin.wispadmin.util.FaceModelFileResolver
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Service
class FacePhotoDescriptorService(
    private val facePhotoPreprocessorService: FacePhotoPreprocessorService,
    private val faceModelFileResolver: FaceModelFileResolver,
    private val faceEmbeddingProperties: FaceEmbeddingProperties,
    private val faceLivenessService: FaceLivenessService
) {
    private val logger = LoggerFactory.getLogger(FacePhotoDescriptorService::class.java)
    private val modelLock = Any()

    @Volatile
    private var model: ZooModel<Image, FloatArray>? = null

    // Calienta el modelo al iniciar Spring para que el primer login no espere la carga de DJL.
    @PostConstruct
    fun warmUpModel() {
        Thread {
            runCatching { ensureModel() }
                .onSuccess { logger.info("Motor facial DJL listo para generar descriptores.") }
                .onFailure { logger.warn("Motor facial DJL aun no pudo calentarse: {}", it.message) }
        }.apply {
            name = "face-djl-warmup"
            isDaemon = true
            start()
        }
    }

    // Libera el modelo DJL cuando el backend se detiene.
    @PreDestroy
    fun closeModel() {
        model?.close()
        model = null
    }

    // Genera el embedding facial desde la foto recibida.
    // Primero intenta el rostro detectado; si falla por cercania/luz, usa recortes centrados como respaldo.
    fun generateDescriptor(photoBytes: ByteArray): List<Double>? {
        if (photoBytes.isEmpty()) {
            return null
        }

        val liveness = faceLivenessService.evaluate(photoBytes)
        if (!liveness.live) {
            logger.info("Descriptor facial rechazado por liveness pasivo: {}", liveness.reason)
            return null
        }

        return try {
            val croppedFaceBytes = facePhotoPreprocessorService.extractSingleFace(photoBytes)
            if (croppedFaceBytes == null) {
                logger.info("Descriptor facial: no se pudo recortar el rostro detectado; se probaran recortes centrados.")
            }

            val fallbackCrops = if (croppedFaceBytes == null) {
                generateCenteredCropCandidates(photoBytes)
            } else {
                emptyList()
            }
            val candidates = listOfNotNull(croppedFaceBytes) + fallbackCrops + photoBytes
            candidates.firstNotNullOfOrNull { candidateBytes ->
                runCatching { generateDescriptorFromBytes(candidateBytes) }.getOrNull()
            }
        } catch (e: IOException) {
            logger.info("Login facial por foto: no se pudo leer la imagen recibida. {}", e.message)
            null
        } catch (e: TranslateException) {
            logger.info("Login facial por foto: DJL no pudo generar descriptor. {}", e.message)
            null
        } catch (e: ModelException) {
            throw IllegalStateException("No se pudo cargar el modelo facial DJL.", e)
        }
    }

    // Genera descriptores alternativos de la misma foto para mejorar el reconocimiento con poca luz o rostro cercano.
    // Se compara primero el rostro detectado, luego recortes centrados y al final la imagen completa.
    fun generateDescriptorCandidates(photoBytes: ByteArray): List<List<Double>> {
        if (photoBytes.isEmpty()) {
            return emptyList()
        }

        val liveness = faceLivenessService.evaluate(photoBytes)
        if (!liveness.live) {
            logger.info("Descriptores candidatos rechazados por liveness pasivo: {}", liveness.reason)
            return emptyList()
        }

        return try {
            val descriptors = mutableListOf<List<Double>>()
            val croppedFaceBytes = facePhotoPreprocessorService.extractSingleFace(photoBytes)

            croppedFaceBytes?.let { faceBytes ->
                generateDescriptorFromBytes(faceBytes)?.let { descriptors.add(it) }
            }

            generateCenteredCropCandidates(photoBytes).forEach { candidateBytes ->
                generateDescriptorFromBytes(candidateBytes)?.let { descriptor ->
                    descriptors.add(descriptor)
                }
            }

            generateDescriptorFromBytes(photoBytes)?.let { fullImageDescriptor ->
                descriptors.add(fullImageDescriptor)
            }

            descriptors
        } catch (e: IOException) {
            logger.info("Login facial por foto: no se pudo leer la imagen recibida. {}", e.message)
            emptyList()
        } catch (e: TranslateException) {
            logger.info("Login facial por foto: DJL no pudo generar descriptor candidato. {}", e.message)
            emptyList()
        } catch (e: ModelException) {
            throw IllegalStateException("No se pudo cargar el modelo facial DJL.", e)
        }
    }

    // Crea recortes centrados para casos donde el detector no encuentra bien una cara cercana.
    // Estos recortes no validan identidad; solo preparan alternativas para el mismo extractor DJL.
    private fun generateCenteredCropCandidates(photoBytes: ByteArray): List<ByteArray> {
        return try {
            val image = ImageFactory.getInstance().fromInputStream(ByteArrayInputStream(photoBytes))
            val imageWidth = image.width
            val imageHeight = image.height
            if (imageWidth <= 0 || imageHeight <= 0) return emptyList()

            listOf(
                FacePreprocessConstants.CENTER_CROP_SPECS
            ).flatten().mapNotNull { spec ->
                createCenteredCrop(image, imageWidth, imageHeight, spec)
            }
        } catch (e: Exception) {
            logger.info("Descriptor facial: no se pudieron crear recortes centrados. {}", e.message)
            emptyList()
        }
    }

    // Recorta una zona central del frame y la guarda como JPG para alimentar el extractor facial.
    private fun createCenteredCrop(
        image: Image,
        imageWidth: Int,
        imageHeight: Int,
        spec: FacePreprocessConstants.CenterCropSpec
    ): ByteArray? {
        val cropWidth = max(1, (imageWidth * spec.widthRatio).roundToInt())
        val cropHeight = max(1, (imageHeight * spec.heightRatio).roundToInt())
        val centerX = imageWidth / 2.0
        val centerY = imageHeight * spec.centerYRatio

        val left = min(max(0, (centerX - cropWidth / 2.0).roundToInt()), imageWidth - 1)
        val top = min(max(0, (centerY - cropHeight / 2.0).roundToInt()), imageHeight - 1)
        val right = min(imageWidth, left + cropWidth)
        val bottom = min(imageHeight, top + cropHeight)
        val width = max(1, right - left)
        val height = max(1, bottom - top)

        val croppedImage = image.getSubImage(left, top, width, height)
        return ByteArrayOutputStream().use { output ->
            croppedImage.save(output, "jpg")
            output.toByteArray()
        }
    }

    // Ejecuta el extractor DJL sobre la imagen preparada y devuelve el vector numerico.
    private fun generateDescriptorFromBytes(imageBytes: ByteArray): List<Double>? {
        val image = ImageFactory.getInstance().fromInputStream(ByteArrayInputStream(imageBytes))
        return createPredictor().use { predictor ->
            val descriptor = predictor.predict(image)
            if (descriptor.isEmpty()) {
                null
            } else {
                descriptor.map { it.toDouble() }
            }
        }
    }

    // Crea un predictor nuevo por solicitud; el modelo pesado queda cargado y reutilizado.
    private fun createPredictor(): Predictor<Image, FloatArray> {
        return ensureModel().newPredictor()
    }

    // Carga una sola vez el modelo FaceNet/PyTorch usado por DJL para generar embeddings.
    private fun ensureModel(): ZooModel<Image, FloatArray> {
        model?.let { return it }

        return synchronized(modelLock) {
            model ?: loadModel().also { loaded ->
                model = loaded
            }
        }
    }

    // Configura DJL segun el motor elegido. Por defecto usa el modelo PyTorch actual;
    // si face.embedding.engine=OnnxRuntime carga un modelo ArcFace/InsightFace con su traductor.
    private fun loadModel(): ZooModel<Image, FloatArray> {
        val modelFile = resolveLocalModelFile()

        if (faceEmbeddingProperties.engine.equals("OnnxRuntime", ignoreCase = true)) {
            logger.info("Cargando modelo facial ONNX (ArcFace) desde {}.", faceEmbeddingProperties.modelPath)
            val (mean, std) = parseNormalize(faceEmbeddingProperties.normalize)
            val criteria = Criteria.builder()
                .setTypes(Image::class.java, FloatArray::class.java)
                .optModelUrls(modelFile.toURI().toString())
                .optModelName(faceEmbeddingProperties.modelName)
                .optTranslator(
                    ArcFaceFeatureTranslator(
                        inputSize = faceEmbeddingProperties.inputSize,
                        mean = mean,
                        std = std,
                        l2Normalize = faceEmbeddingProperties.l2Normalize
                    )
                )
                .optEngine("OnnxRuntime")
                .optProgress(ProgressBar())
                .build()

            return criteria.loadModel()
        }

        val criteria = Criteria.builder()
            .setTypes(Image::class.java, FloatArray::class.java)
            .optModelUrls(modelFile.toURI().toString())
            .optModelName(faceEmbeddingProperties.modelName)
            .optArgument("normalize", faceEmbeddingProperties.normalize)
            .optTranslatorFactory(ImageFeatureExtractorFactory())
            .optEngine("PyTorch")
            .optProgress(ProgressBar())
            .build()

        return criteria.loadModel()
    }

    // Convierte la cadena "m,m,m,s,s,s" (fracciones de 255) en arreglos de media y desviacion.
    private fun parseNormalize(raw: String): Pair<FloatArray, FloatArray> {
        val values = raw.split(",").mapNotNull { it.trim().toFloatOrNull() }
        if (values.size < 6) {
            return Pair(
                floatArrayOf(0.5f, 0.5f, 0.5f),
                floatArrayOf(0.5f, 0.5f, 0.5f)
            )
        }
        return Pair(
            floatArrayOf(values[0], values[1], values[2]),
            floatArrayOf(values[3], values[4], values[5])
        )
    }

    // Valida que el modelo exista localmente antes de pedirle a DJL que lo cargue.
    private fun resolveLocalModelFile() =
        faceModelFileResolver.resolve(faceEmbeddingProperties.modelPath, faceEmbeddingProperties.modelName)
}
