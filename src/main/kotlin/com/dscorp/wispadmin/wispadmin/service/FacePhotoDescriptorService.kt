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
import com.dscorp.wispadmin.wispadmin.util.FaceModelFileResolver
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
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
    @Value("\${face.login.djl-model-path:classpath:models/face_feature.zip}")
    private val djlModelPath: String,
    @Value("\${face.login.djl-model-name:face_feature}")
    private val djlModelName: String,
    private val facePhotoPreprocessorService: FacePhotoPreprocessorService,
    private val faceModelFileResolver: FaceModelFileResolver
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
                CenterCropSpec(widthRatio = 0.82, heightRatio = 0.92, centerYRatio = 0.48),
                CenterCropSpec(widthRatio = 0.68, heightRatio = 0.82, centerYRatio = 0.44),
                CenterCropSpec(widthRatio = 0.96, heightRatio = 0.96, centerYRatio = 0.50)
            ).mapNotNull { spec ->
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
        spec: CenterCropSpec
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

    private data class CenterCropSpec(
        val widthRatio: Double,
        val heightRatio: Double,
        val centerYRatio: Double
    )

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

    // Configura DJL con el traductor oficial de extraccion de caracteristicas de imagen.
    private fun loadModel(): ZooModel<Image, FloatArray> {
        val modelFile = resolveLocalModelFile()
        val normalize = listOf(
            127.5f / 255.0f,
            127.5f / 255.0f,
            127.5f / 255.0f,
            128.0f / 255.0f,
            128.0f / 255.0f,
            128.0f / 255.0f
        ).joinToString(",")

        val criteria = Criteria.builder()
            .setTypes(Image::class.java, FloatArray::class.java)
            .optModelUrls(modelFile.toURI().toString())
            .optModelName(djlModelName)
            .optArgument("normalize", normalize)
            .optTranslatorFactory(ImageFeatureExtractorFactory())
            .optEngine("PyTorch")
            .optProgress(ProgressBar())
            .build()

        return criteria.loadModel()
    }

    // Valida que el zip del modelo exista localmente antes de pedirle a DJL que lo cargue.
    private fun resolveLocalModelFile() =
        faceModelFileResolver.resolve(djlModelPath, "face_feature")
}
