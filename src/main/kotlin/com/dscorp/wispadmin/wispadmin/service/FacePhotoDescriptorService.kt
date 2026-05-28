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
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

@Service
class FacePhotoDescriptorService(
    @Value("\${face.login.djl-model-path:C:/ispadmin/models/face_feature.zip}")
    private val djlModelPath: String,
    @Value("\${face.login.djl-model-name:face_feature}")
    private val djlModelName: String
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

    // Convierte la foto recibida desde Android en un embedding facial usando DJL.
    fun generateDescriptor(photoBytes: ByteArray): List<Double>? {
        if (photoBytes.isEmpty()) {
            return null
        }

        return try {
            val image = ImageFactory.getInstance().fromInputStream(ByteArrayInputStream(photoBytes))
            createPredictor().use { predictor ->
                val descriptor = predictor.predict(image)
                if (descriptor.isEmpty()) {
                    null
                } else {
                    descriptor.map { it.toDouble() }
                }
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
    private fun resolveLocalModelFile(): File {
        val modelFile = File(djlModelPath)
        if (!modelFile.exists() || !modelFile.isFile) {
            throw IllegalStateException(
                "No se encontro el modelo facial DJL en $djlModelPath. " +
                    "Coloca face_feature.zip en esa ruta antes de iniciar el backend."
            )
        }
        return modelFile
    }
}
