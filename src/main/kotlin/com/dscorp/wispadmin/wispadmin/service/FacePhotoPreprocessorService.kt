package com.dscorp.wispadmin.wispadmin.service

import ai.djl.ModelException
import ai.djl.inference.Predictor
import ai.djl.modality.cv.Image
import ai.djl.modality.cv.ImageFactory
import ai.djl.modality.cv.output.DetectedObjects
import ai.djl.repository.zoo.Criteria
import ai.djl.repository.zoo.ZooModel
import ai.djl.training.util.ProgressBar
import ai.djl.translate.TranslateException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Detecta y recorta un unico rostro antes de generar su descriptor.
 *
 * Esta validacion ocurre en el backend para no confiar solamente en Android:
 * un cliente externo tambien podria llamar directamente al endpoint.
 */
@Service
class FacePhotoPreprocessorService(
    @Value("\${face.login.djl-detector-model-path:models/ultranet.zip}")
    private val detectorModelPath: String,
    @Value("\${face.login.djl-detector-model-name:ultranet}")
    private val detectorModelName: String,
    @Value("\${face.login.djl-model-path:}")
    private val descriptorModelPath: String
) {
    private val logger = LoggerFactory.getLogger(FacePhotoPreprocessorService::class.java)
    private val modelLock = Any()

    @Volatile
    private var detectorModel: ZooModel<Image, DetectedObjects>? = null

    companion object {
        private const val CONFIDENCE_THRESHOLD = 0.72
        private const val NMS_THRESHOLD = 0.45
        private const val TOP_K = 5000
        private const val FACE_MARGIN_RATIO = 0.18
        private const val MIN_FACE_WIDTH_RATIO = 0.12

        private val VARIANCE = doubleArrayOf(0.1, 0.2)
        private val SCALES = arrayOf(
            intArrayOf(10, 16, 24),
            intArrayOf(32, 48),
            intArrayOf(64, 96),
            intArrayOf(128, 192, 256)
        )
        private val STEPS = intArrayOf(8, 16, 32, 64)
    }

    /**
     * Carga el detector al iniciar Spring para que el primer login no pague
     * el costo de inicializacion del modelo.
     */
    @PostConstruct
    fun warmUpDetector() {
        Thread {
            runCatching { ensureDetectorModel() }
                .onSuccess { logger.info("Detector facial DJL ultranet listo.") }
                .onFailure { logger.warn("Detector facial DJL aun no pudo calentarse: {}", it.message) }
        }.apply {
            name = "face-detector-djl-warmup"
            isDaemon = true
            start()
        }
    }

    /**
     * Libera el modelo nativo cuando Spring detiene la aplicacion.
     */
    @PreDestroy
    fun closeDetector() {
        detectorModel?.close()
        detectorModel = null
    }

    /**
     * Devuelve una imagen JPEG que contiene solamente el rostro.
     *
     * Retorna null si la foto es invalida, no contiene rostros, contiene mas
     * de uno o el rostro esta demasiado lejos para generar un descriptor
     * confiable.
     */
    fun extractSingleFace(photoBytes: ByteArray): ByteArray? {
        if (photoBytes.isEmpty()) return null

        return try {
            val image = ImageFactory.getInstance()
                .fromInputStream(ByteArrayInputStream(photoBytes))
            val detection = createDetectorPredictor().use { predictor ->
                predictor.predict(image)
            }

            if (detection.numberOfObjects != 1) {
                logger.info(
                    "Foto facial rechazada: se esperaba exactamente un rostro, detectados={}.",
                    detection.numberOfObjects
                )
                return null
            }

            val detectedFace = detection.item<DetectedObjects.DetectedObject>(0)
            val bounds = detectedFace.boundingBox.bounds
            val faceWidthRatio = bounds.width

            if (faceWidthRatio < MIN_FACE_WIDTH_RATIO) {
                logger.info(
                    "Foto facial rechazada: rostro demasiado lejano. widthRatio={}.",
                    faceWidthRatio
                )
                return null
            }

            val crop = calculateCrop(
                imageWidth = image.width,
                imageHeight = image.height,
                xRatio = bounds.x,
                yRatio = bounds.y,
                widthRatio = bounds.width,
                heightRatio = bounds.height
            )

            val faceImage = image.getSubImage(crop.x, crop.y, crop.width, crop.height)
            ByteArrayOutputStream().use { output ->
                faceImage.save(output, "jpg")
                output.toByteArray()
            }
        } catch (e: IOException) {
            logger.info("Foto facial rechazada: no se pudo leer o recortar la imagen. {}", e.message)
            null
        } catch (e: TranslateException) {
            logger.info("Foto facial rechazada: ultranet no pudo analizar la imagen. {}", e.message)
            null
        } catch (e: ModelException) {
            throw IllegalStateException("No se pudo cargar el detector facial DJL.", e)
        }
    }

    /**
     * Crea un predictor por solicitud. El modelo pesado permanece cargado y
     * se comparte de forma segura entre los intentos de reconocimiento.
     */
    private fun createDetectorPredictor(): Predictor<Image, DetectedObjects> {
        return ensureDetectorModel().newPredictor()
    }

    /**
     * Carga ultranet una sola vez desde el disco local del servidor.
     */
    private fun ensureDetectorModel(): ZooModel<Image, DetectedObjects> {
        detectorModel?.let { return it }

        return synchronized(modelLock) {
            detectorModel ?: loadDetectorModel().also { loaded ->
                detectorModel = loaded
            }
        }
    }

    /**
     * Configura DJL con el traductor del detector liviano ultranet.
     */
    private fun loadDetectorModel(): ZooModel<Image, DetectedObjects> {
        val modelFile = resolveDetectorModelFile()
        val translator = FaceDetectionTranslator(
            confidenceThreshold = CONFIDENCE_THRESHOLD,
            nmsThreshold = NMS_THRESHOLD,
            variance = VARIANCE,
            topK = TOP_K,
            scales = SCALES,
            steps = STEPS
        )

        val criteria = Criteria.builder()
            .setTypes(Image::class.java, DetectedObjects::class.java)
            .optModelUrls(modelFile.toURI().toString())
            .optModelName(detectorModelName)
            .optTranslator(translator)
            .optEngine("PyTorch")
            .optProgress(ProgressBar())
            .build()

        return criteria.loadModel()
    }

    /**
     * Valida que el detector exista localmente para evitar descargas durante
     * la ejecucion del backend.
     */
    private fun resolveDetectorModelFile(): File {
        val modelFile = resolveExistingDetectorModelFile()
        if (!modelFile.exists() || !modelFile.isFile) {
            throw IllegalStateException(
                "No se encontro el detector facial DJL en $detectorModelPath. " +
                    "Coloca ultranet.zip en models/ o junto al modelo principal antes de iniciar el backend."
            )
        }
        return modelFile
    }

    private fun resolveExistingDetectorModelFile(): File {
        val configuredFile = File(detectorModelPath)
        if (configuredFile.exists() && configuredFile.isFile) return configuredFile

        val descriptorFile = descriptorModelPath.takeIf { it.isNotBlank() }?.let { File(it) }
        val siblingDetector = descriptorFile?.parentFile?.let { File(it, configuredFile.name) }
        if (siblingDetector?.exists() == true && siblingDetector.isFile) return siblingDetector

        return configuredFile
    }

    /**
     * Amplia ligeramente la caja detectada para conservar el contorno facial
     * sin salir de los limites de la fotografia.
     */
    private fun calculateCrop(
        imageWidth: Int,
        imageHeight: Int,
        xRatio: Double,
        yRatio: Double,
        widthRatio: Double,
        heightRatio: Double
    ): CropArea {
        val faceX = xRatio * imageWidth
        val faceY = yRatio * imageHeight
        val faceWidth = widthRatio * imageWidth
        val faceHeight = heightRatio * imageHeight
        val horizontalMargin = faceWidth * FACE_MARGIN_RATIO
        val verticalMargin = faceHeight * FACE_MARGIN_RATIO

        val left = max(0.0, faceX - horizontalMargin).roundToInt()
        val top = max(0.0, faceY - verticalMargin).roundToInt()
        val right = min(imageWidth.toDouble(), faceX + faceWidth + horizontalMargin).roundToInt()
        val bottom = min(imageHeight.toDouble(), faceY + faceHeight + verticalMargin).roundToInt()

        return CropArea(
            x = left,
            y = top,
            width = max(1, right - left),
            height = max(1, bottom - top)
        )
    }

    private data class CropArea(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    )
}
